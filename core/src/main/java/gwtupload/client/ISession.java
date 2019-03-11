package gwtupload.client;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestBuilder.Method;
import com.google.gwt.http.client.Header;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.RequestException;
import com.google.gwt.http.client.Response;
import com.google.gwt.jsonp.client.JsonpRequestBuilder;
import com.google.gwt.user.client.Cookies;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.xml.client.XMLParser;

import java.util.List;
import java.util.Map.Entry;

import static gwtupload.shared.UConsts.PARAM_SESSION;
import static gwtupload.shared.UConsts.TAG_SESSION_ID;

public interface ISession {

  public static class CORSSessionParameter extends Session {
    @Override
    protected void setSessionId(String s) {
      super.setSessionId(s);
      s = s == null? "" : (";jsessionid=" + s);
      servletPath = servletPath.replaceFirst("^(.+)(/[^/\\?;]*)(;[^/\\?]*|)(\\?|/$|$)(.*)", "$1$2" + s + "$4$5");
      System.err.println("CORS Session: " + servletPath);
    }
  }

  public static class CORSSession extends Session {
    @Override
    protected RequestBuilder createRequest(Method method, int timeout, String... params) {
      RequestBuilder req =  super.createRequest(method, timeout, params);
      req.setIncludeCredentials(true);
      return req;
    }
  }

  public static class Session implements ISession {
    String sessionId;
    String servletPath = "servlet.gupld";

    public static ISession createSession(String path, RequestCallback callback) {
      Session ret;
      if (path.startsWith("http")) {
        ret = GWT.create(CORSSession.class);
      } else {
        ret = GWT.create(Session.class);
      }
      ret.servletPath = path;
      ret.getSession(callback);
      return ret;
    }

    /**
     * Sends a request to the server in order to get the session cookie,
     * when the response with the session comes, it submits the form.
     *
     * This is needed because this client application usually is part of
     * static files, and the server doesn't set the session until dynamic pages
     * are requested.
     *
     * If we submit the form without a session, the server creates a new
     * one and send a cookie in the response, but the response with the
     * cookie comes to the client at the end of the request, and in the
     * meanwhile the client needs to know the session in order to ask
     * the server for the upload status.
     */
    public void getSession(final RequestCallback callback) {
      sendRequest("session", new RequestCallback() {
        public void onResponseReceived(Request request, Response response) {
          String s  = Cookies.getCookie("JSESSIONID");
          if (s == null) {
            s = Utils.getXmlNodeValue(XMLParser.parse(response.getText()), TAG_SESSION_ID);
          }
          setSessionId(s);
          callback.onResponseReceived(request, response);
        }
        public void onError(Request request, Throwable exception) {
          setSessionId(null);
          callback.onError(request, exception);
        }
      }, PARAM_SESSION + "=true");
    }

    protected void setSessionId(String s) {
      sessionId = s;
    }

    public String getServletPath() {
      return servletPath;
    }

    public void sendRequest(String payload, RequestCallback callback, String... params) {
      // Using a reusable builder makes IE fail
      RequestBuilder reqBuilder = createRequest(RequestBuilder.GET, DEFAULT_AJAX_TIMEOUT, params);
      try {
        reqBuilder.sendRequest(payload, callback);
      } catch (RequestException e) {
        callback.onError(null, e);
      }
    }

    /**
     * 发送请求获取Nginx进度
     */
    public void sendNginxRequest(RequestCallback callback, String progressId, String url) {
        RequestBuilder reqBuilder = new RequestBuilder(RequestBuilder.GET, url);
        reqBuilder.setTimeoutMillis(DEFAULT_AJAX_TIMEOUT);
        reqBuilder.setHeader("X-Progress-ID", progressId);
        try {
          reqBuilder.setCallback(callback);
          reqBuilder.send();
        } catch (RequestException e) {
          callback.onError(null, e);
        }
    }
    
    static class Feed extends JavaScriptObject {
        protected Feed() {}

        public final native Object getReceived() /*-{
            return this.received;
        }-*/;
        
        public final native Object getSize() /*-{
            return this.size;
        }-*/;
        
        public final native Object getState() /*-{
            return this.state;
        }-*/;
        
        public final native Object getEntries() /*-{
              return this.feed;
        }-*/;
    }
    
    class JSONPResponse extends Response {
        @Override
        public String getHeader(String header) {
            return null;
        }

        @Override
        public Header[] getHeaders() {
            return null;
        }

        @Override
        public String getHeadersAsString() {
            return null;
        }

        @Override
        public int getStatusCode() {
            return 0;
        }

        @Override
        public String getStatusText() {
            return null;
        }

        private String text = "";
        @Override
        public String getText() {
            return text;
        }
        
        public void setext(String text) {
            this.text = text;
        }
    }
    
    /**
     * 跨域下的Nginx上传
     * @param url
     */
    public void sendJSONPNginxRequest(final RequestCallback callback, String url) {
        JsonpRequestBuilder jsonp = new JsonpRequestBuilder();
        jsonp.requestObject(url, new AsyncCallback<Feed>() {

            @Override
            public void onFailure(Throwable caught) {
                callback.onError(null, caught);
            }

            @Override
            public void onSuccess(Feed feed) {
                if (feed != null) {
                    JSONPUploadSuccess(callback, feed);
                } else {
                    GWT.log("Nginx模式下，JSONP模式未获取到返回值。");
                }
            }
        });
    }
    
    private void JSONPUploadSuccess(RequestCallback callback, Feed feed) {
        Object state = feed.getState();
        Object received = feed.getReceived();
        Object size = feed.getSize();
        
        String result = "{state:'"+state.toString().replace("\"", "")+"',received:'"+received+"',size:'"+size+"'}";
        
        JSONPResponse response = new JSONPResponse();
        response.setext(result);
        callback.onResponseReceived(null, response);
    }
    
    protected RequestBuilder createRequest(Method method, int timeout, String...params) {
      RequestBuilder reqBuilder = new RequestBuilder(RequestBuilder.GET, composeURL(params));
      reqBuilder.setTimeoutMillis(timeout);
      return reqBuilder;
    }

    public String composeURL(String... params) {
      String ret = servletPath;
      ret = ret.replaceAll("[\\?&]+$", "");
      String sep = ret.contains("?") ? "&" : "?";
      for (String par : params) {
        ret += sep + par;
        sep = "&";
      }
      for (Entry<String, List<String>> e : Window.Location.getParameterMap().entrySet()) {
        ret += sep + e.getKey() + "=" + e.getValue().get(0);
      }
      ret += sep + "random=" + Math.random();
      return ret;
    }
  }

  static final int DEFAULT_AJAX_TIMEOUT = 10000;

  public void getSession(RequestCallback callback);

  public String composeURL(String... params);

  public void sendRequest(String name, RequestCallback callback, String... params);
  
  public void sendNginxRequest(RequestCallback callback, String id, String url);
  
  public void sendJSONPNginxRequest(RequestCallback callback, String url);

  public String getServletPath();
}
