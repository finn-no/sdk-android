package com.schibsted.android.sdk.request;

import android.os.AsyncTask;

import com.schibsted.android.sdk.SPiDClient;
import com.schibsted.android.sdk.accesstoken.SPiDAccessToken;
import com.schibsted.android.sdk.exceptions.SPiDException;
import com.schibsted.android.sdk.listener.SPiDRequestListener;
import com.schibsted.android.sdk.logger.SPiDLogger;
import com.schibsted.android.sdk.reponse.SPiDResponse;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Contains a request to SPiD, note that each request can only be used once since it extends <code>AsyncTask</code>
 */
public class SPiDRequest extends AsyncTask<Void, Void, SPiDResponse> {

    public static final String GET = "GET";
    public static final String POST = "POST";
    private static final Integer MaxRetryCount = 0;
    protected SPiDRequestListener listener;
    private String url;
    private String method;
    private Map<String, String> headers;
    private Map<String, String> query;
    private Map<String, String> body;
    private Integer retryCount;
    private Integer maxRetryCount;

    /**
     * Constructor for the SPiDRequest
     *
     * @param method   The http method to be used
     * @param url      The request url
     * @param listener Called on completion or error, can be <code>null</code>
     */
    public SPiDRequest(String method, String url, SPiDRequestListener listener) {
        super();
        this.url = url;
        this.method = method;
        this.headers = new HashMap<String, String>();
        this.query = new HashMap<String, String>();
        this.body = new HashMap<String, String>();

        this.listener = listener;

        this.retryCount = 0;
        this.maxRetryCount = MaxRetryCount;

        SPiDLogger.log("Created request: " + url);
    }

    /**
     * Constructor for the SPiDRequest, sets default method to GET
     *
     * @param url      The request url
     * @param listener Called on completion or error, can be <code>null</code>
     */
    public SPiDRequest(String url, SPiDRequestListener listener) {
        this("GET", url, listener);
    }

    /**
     * @return The http method for the request
     */
    public String getMethod() {
        return method;
    }

    /**
     * Adds a key/value to the query
     *
     * @param key   The key
     * @param value The value
     */
    public void addQueryParameter(String key, String value) {
        query.put(key, value);
    }

    /**
     * Adds a key/value to the body
     *
     * @param key   The key
     * @param value The value
     */
    public void addBodyParameter(String key, String value) {
        body.put(key, value);
    }

    /**
     * @return The complete URL with the query
     * @throws UnsupportedEncodingException
     */
    public String getCompleteURL() throws UnsupportedEncodingException {
        return url + getQueryAsString();
    }

    /**
     * Encodes a key/value to "key=value" for use in the query string
     *
     * @param key   The key to be encoded
     * @param value The value to be encoded
     * @return The encoded string
     * @throws UnsupportedEncodingException
     */
    private String encodeURLParameter(String key, String value) throws UnsupportedEncodingException {
        return String.format("%s=%s", URLEncoder.encode(key, "UTF-8"), URLEncoder.encode(value, "UTF-8"));
    }

    /**
     * Generates the query string
     *
     * @return The query
     * @throws UnsupportedEncodingException
     */
    private String getQueryAsString() throws UnsupportedEncodingException {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : query.entrySet()) {
            if (builder.length() > 0)
                builder.append('&');
            else
                builder.append('?');
            builder.append(encodeURLParameter(entry.getKey(), entry.getValue()));
        }
        return builder.toString();
    }

    /**
     * Creates a copy of the <code>SPiDRequest</code>, this is used since AsyncTask can only be used once
     *
     * @return A copy of the <code>SPiDRequest</code>
     */
    private SPiDRequest copy() {
        SPiDRequest request = new SPiDRequest(method, url, listener);
        request.retryCount = retryCount;
        request.setHeaders(headers);
        request.setQuery(query);
        request.setBody(body);
        return request;
    }

    /**
     * @param headers The http headers
     */
    private void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    /**
     * @param query The query parameters
     */
    private void setQuery(Map<String, String> query) {
        this.query = query;
    }

    /**
     * @param body The htpp body
     */
    private void setBody(Map<String, String> body) {
        this.body = body;
    }

    /**
     * Runs the request and receives a response in a background thread.
     *
     * @param voids Unused parameter required by <code>AsyncTask</code>
     * @return A <code>SPiDResponse</code>
     */
    @Override
    protected SPiDResponse doInBackground(Void... voids) {
        try {
            HttpRequestBase httpRequest;
            if (this.method.toUpperCase().equals("POST")) {
                httpRequest = new HttpPost(url);

                List<NameValuePair> postList = new ArrayList<NameValuePair>();
                for (Map.Entry<String, String> entry : body.entrySet()) {
                    postList.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
                }

                ((HttpPost) httpRequest).setEntity(new UrlEncodedFormEntity(postList, "UTF-8"));
            } else {
                httpRequest = new HttpGet(url + getQueryAsString());
            }

            // Add custom User-Agent
            headers.put("User-Agent", SPiDClient.getInstance().getConfig().getUserAgent());

            List<Header> headerList = new ArrayList<Header>();
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                headerList.add(new BasicHeader(entry.getKey(), entry.getValue()));
            }

            Header[] headerArray = new Header[headerList.size()];
            httpRequest.setHeaders(headerList.toArray(headerArray));

            HttpClientParams.setRedirecting(httpRequest.getParams(), false);

            HttpClient httpClient = new DefaultHttpClient();
            HttpResponse httpResponse = httpClient.execute(httpRequest);

            return new SPiDResponse(httpResponse);
        } catch (IOException e) {
            return new SPiDResponse(e);
        } catch (Exception e) {
            return new SPiDResponse(e);
        }
    }

    /**
     * Runs on the UI thread after doInBackground
     *
     * @param response The <code>SPiDResponse</code> created in doInBackground
     */
    @Override
    protected void onPostExecute(SPiDResponse response) {
        super.onPostExecute(response);
        doOnPostExecute(response);
    }

    /**
     * Checks the <code>SPiDResponse</code> for errors,handles retries and invokes the callback listener
     *
     * @param response The <code>SPiDResponse</code> created in doInBackground
     */
    protected void doOnPostExecute(SPiDResponse response) {
        Exception exception = response.getException();
        if (exception != null) {
            if (exception instanceof IOException) {
                listener.onIOException((IOException) exception);
            } else if (exception instanceof SPiDException) {
                String error = ((SPiDException) exception).getError();
                if (error != null && (error.equals(SPiDException.EXPIRED_TOKEN) || error.equals(SPiDException.INVALID_TOKEN))) {
                    if (retryCount < maxRetryCount) {
                        SPiDRequest request = this.copy();
                        request.retryCount++;
                        SPiDClient.getInstance().addWaitingRequest(request);
                        SPiDClient.getInstance().refreshAccessToken(null);
                        SPiDLogger.log("Retrying attempt: " + request.retryCount + " for request: " + request.url);
                    } else {
                        SPiDClient.getInstance().clearAccessToken();
                        listener.onSPiDException((SPiDException) exception);
                    }
                } else {
                    listener.onSPiDException((SPiDException) exception);
                }
            } else {
                listener.onException(exception);
            }
        } else {
            listener.onComplete(response);
        }
    }

    /**
     * Execute request, can only be called once
     */
    public void execute() {
        execute((Void) null);
    }

    /**
     * Execute request authorized request, appends oauth token if needed
     */
    public void executeAuthorizedRequest() {
        SPiDAccessToken accessToken = SPiDClient.getInstance().getAccessToken();
        if (method.equals(GET)) {
            if (!url.contains("oauth_token") && accessToken != null) {
                if (url.contains("?")) {
                    url = url + "&oauth_token=" + accessToken.getAccessToken();
                } else {
                    url = url + "?oauth_token=" + accessToken.getAccessToken();
                }
            }
        } else {
            if (!body.containsKey("oauth_token") && accessToken != null) {
                addBodyParameter("oauth_token", accessToken.getAccessToken());
            }
        }
        execute();
    }

    /**
     * Maximum retry count for the request, used when token expires and the request needs to be retried after a new token has been obtained.
     *
     * @param maxRetryCount Max retry attempts
     */
    public void setMaxRetryCount(int maxRetryCount) {
        this.maxRetryCount = maxRetryCount;
    }
}
