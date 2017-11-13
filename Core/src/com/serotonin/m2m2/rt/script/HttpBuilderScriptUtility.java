/**
 * Copyright (C) 2017 Infinite Automation Software. All rights reserved.
 * @author Phillip Dunlap
 */
package com.serotonin.m2m2.rt.script;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import jdk.nashorn.api.scripting.JSObject;

import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.eclipse.jetty.http.HttpMethod;
import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.serotonin.m2m2.Common;
import com.serotonin.m2m2.web.mvc.spring.MangoRestSpringConfiguration;
import com.serotonin.web.http.HttpUtils4;

//import com.serotonin.m2m2.module.definitions.permissions.SuperadminPermissionDefinition;


@SuppressWarnings("restriction")
public class HttpBuilderScriptUtility {
    public static final String CONTEXT_KEY = "HttpBuilder";
    
    private int retried = 0;
    private HttpUriRequest request = null;
    private HttpResponse response;
    private ScriptHttpCallback errorCallback;
    private ScriptHttpCallback responseCallback;
    private ScriptExceptionCallback exceptionCallback;
    private Exception thrown;
    private List<Integer> okayStatus = new ArrayList<Integer>(1);
    
    public HttpBuilderScriptUtility(ScriptPermissions permissions) {
        okayStatus.add(HttpStatus.OK.value());
    }
    
    public HttpResponse getResponse() {
        return response;
    }
    
    public HttpUriRequest getStagedRequest() {
        return request;
    }
    
    public void setStagedRequest(HttpUriRequest request) {
        this.request = request;
    }
    
    public int getRetried() {
        return retried;
    }
    
    public void setOkayStatusArray(int[] statusCodes) {
        okayStatus.clear();
        for(int i : statusCodes)
            okayStatus.add(i);
    }
    
    /*
     * Provided properly named functions and spicy JavaScript shorthands
     */
    public HttpBuilderScriptUtility err(ScriptHttpCallback errorCallback) {
        this.errorCallback = errorCallback;
        return this;
    }
    
    public HttpBuilderScriptUtility resp(ScriptHttpCallback responseCallback) {
        this.responseCallback = responseCallback;
        return this;
    }
    
    public HttpBuilderScriptUtility excp(ScriptExceptionCallback exceptionCallback) {
        this.exceptionCallback = exceptionCallback;
        return this;
    }
    
    public HttpBuilderScriptUtility setErrorCallback(ScriptHttpCallback errorCallback) {
        this.errorCallback = errorCallback;
        return this;
    }
    
    public HttpBuilderScriptUtility setResponseCallback(ScriptHttpCallback responseCallback) {
        this.responseCallback = responseCallback;
        return this;
    }
    
    public HttpBuilderScriptUtility setExceptionCallback(ScriptExceptionCallback exceptionCallback) {
        this.exceptionCallback = exceptionCallback;
        return this;
    }
    
    @SuppressWarnings({"unchecked"})
    public Object request(Map<String, Object> request) {
        reset();
        try {
            if(request.containsKey("excp"))
                exceptionCallback = (ScriptExceptionCallback)request.get("excp");
            else if(request.containsKey("exceptionCallback"))
                exceptionCallback = (ScriptExceptionCallback)request.get("exceptionCallback");
            
            if(request.containsKey("err"))
                errorCallback = new ScriptObjectMirrorCallbackWrapper((JSObject)request.get("err"));
            else if(request.containsKey("errorCallback"))
                errorCallback = new ScriptObjectMirrorCallbackWrapper((JSObject)request.get("errorCallback"));
            
            if(request.containsKey("resp"))
                responseCallback = new ScriptObjectMirrorCallbackWrapper((JSObject)request.get("resp"));
            else if(request.containsKey("responseCallback"))
                responseCallback = new ScriptObjectMirrorCallbackWrapper((JSObject)request.get("responseCallback"));
            
            if(!request.containsKey("path")) {
                thrown = new Exception("Must have 'path' attribute to make request.");
                return execute();
            }
            
            String path = (String)request.get("path");
            
            Map<String, Object> headers = null;
            if(request.containsKey("headers"))
                headers = (Map<String, Object>)request.get("headers");
            
            Map<String, Object> parameters = null;
            if(request.containsKey("parameters"))
                parameters = (Map<String, Object>)request.get("parameters");
            
            String content;
            if(request.containsKey("content") && request.get("content") instanceof String)
                content = (String)request.get("content");
            else if(request.containsKey("content")) {
                content = MangoRestSpringConfiguration.getObjectMapper().writeValueAsString(request.get("content"));
            } else
                content = "";
            
            String method;
            if(!request.containsKey("method")) {
                thrown = new Exception("Must have 'method' attribute to make request.");
                return execute();
            } else
                method = (String)request.get("method");
            
            if(HttpMethod.POST.name().equals(method))
                post(path, headers, content, false);
            else if(HttpMethod.GET.name().equals(method))
                get(path, headers, parameters, false);
            else if(HttpMethod.PUT.name().equals(method))
                put(path, headers, content, false);
            else if(HttpMethod.DELETE.name().equals(method))
                delete(path, headers);
            
//            if(checkContentType)
//                checkJsonContentTypeHeader();
            
            return execute();
        } catch(ClassCastException e) {
            thrown = e;
            //throw new ScriptException(e.getMessage());
        } catch(JsonProcessingException e) {
            thrown = e;
        } finally {
            if(thrown != null) {
                if(exceptionCallback != null)
                    exceptionCallback.exception(thrown);
                else if(errorCallback != null)
                    errorCallback.invoke(-1, null, thrown.getMessage());
                thrown = null;
            }
        }
        return null;
    }
    
    public HttpBuilderScriptUtility post(String loc, Map<String, Object> headers, Object content) {
        return post(loc, headers, content, true);
    }
    
    private HttpBuilderScriptUtility post(String loc, Map<String, Object> headers, Object content, boolean reset) {
        if(reset)
            reset();
        
        URI uri = buildUri(loc);
        if(uri == null)
            return this;
        
        HttpPost post = new HttpPost(uri);
        putHeaders(post, headers);
        try {
            if(content instanceof String)
                post.setEntity(new StringEntity((String)content));
            else {
                post.setEntity(new StringEntity(MangoRestSpringConfiguration.getObjectMapper().writeValueAsString(content)));
//                checkJsonContentTypeHeader(post);
            }
            request = post;
        } catch(UnsupportedEncodingException e) {
            thrown = e;
        } catch(JsonProcessingException e) {
            thrown = e;
        }
        return this;
    }
    
    public HttpBuilderScriptUtility put(String loc, Map<String, Object> headers, Object content) {
        return put(loc, headers, content, true);
    }
    
    private HttpBuilderScriptUtility put(String loc, Map<String, Object> headers, Object content, boolean reset) {
        if(reset)
            reset();
        
        URI uri = buildUri(loc);
        if(uri == null)
            return this;
        
        HttpPut put = new HttpPut(uri);
        putHeaders(put, headers);
        try {
            if(content instanceof String)
                put.setEntity(new StringEntity((String)content));
            else {
                put.setEntity(new StringEntity(MangoRestSpringConfiguration.getObjectMapper().writeValueAsString(content)));
//                checkJsonContentTypeHeader(put);
            }
            request = put;
        } catch(UnsupportedEncodingException|JsonProcessingException e) {
            thrown = e;
        }
        
        return this;
    }
    
    public HttpBuilderScriptUtility get(String loc, Map<String, Object> headers, Map<String, Object> parameters) {
        return get(loc, headers, parameters, true);
    }
    
    private HttpBuilderScriptUtility get(String loc, Map<String, Object> headers, Map<String, Object> parameters, boolean reset) {
        if(reset)
            reset();
        
        URI uri = buildUri(loc, parameters);
        if(uri == null)
            return this;
        
        HttpGet get = new HttpGet(uri);
        putHeaders(get, headers);
        request = get;
        return this;
    }
    
    public HttpBuilderScriptUtility delete(String loc, Map<String, Object> headers) {
        return delete(loc, headers, true);
    }
    
    private HttpBuilderScriptUtility delete(String loc, Map<String, Object> headers, boolean reset) {
        if(reset)
            reset();
        
        URI uri = buildUri(loc);
        if(uri == null)
            return this;
        
        HttpDelete delete = new HttpDelete(uri);
        putHeaders(delete, headers);
        request = delete;
        return this;
    }
    
    public Object execute() {
        return execute(false);
    }
    
    public Object retry() {
        return execute(true);
    }
    
    private Object execute(boolean retry) {
        if(!retry && retried > 0) {
            if(exceptionCallback != null)
                return exceptionCallback.exception(new Exception("Request was already executed."));
            if(errorCallback != null)
                return errorCallback.invoke(-1, null, "Request was already executed.");
        }
        
        retried += 1;
        
        if(thrown != null) {
            if(exceptionCallback != null)
                return exceptionCallback.exception(thrown);
            if(errorCallback != null)
                return errorCallback.invoke(-1, null, thrown.getMessage());
        }
        
        try {
            if(request.getHeaders("Content-Type").length == 0)
                request.setHeader("Content-Type", "application/json");
            response = Common.getHttpClient().execute(request);
        } catch(IOException e) {
            if(exceptionCallback != null)
                return exceptionCallback.exception(e);
            else if(errorCallback != null)
                return errorCallback.invoke(-1, null, e.getMessage());
        }
        
        try {   
            for(Integer status : okayStatus)
                if(status.intValue() == response.getStatusLine().getStatusCode()) {
                    if(responseCallback != null)
                        return responseCallback.invoke(status.intValue(), extractHeaders(response), HttpUtils4.readFullResponseBody(response));
                    return null;
                }
            
            if(errorCallback != null)
                return errorCallback.invoke(response.getStatusLine().getStatusCode(), extractHeaders(response), HttpUtils4.readFullResponseBody(response));
        }  catch(IOException e) {
            if(exceptionCallback != null)
                return exceptionCallback.exception(e);
            if(errorCallback != null)
                return errorCallback.invoke(response.getStatusLine().getStatusCode(), extractHeaders(response), "FAILED TO READ BODY: " + e.getMessage());
        }
        return null;
    }
    
    private URI buildUri(String loc) {
        return buildUri(loc, null);
    }
    
    private URI buildUri(String loc, Map<String, Object> parameters) {
        try {
            URIBuilder urib = new URIBuilder(loc);
            if(parameters != null) {
                for(Entry<String, Object> entry : parameters.entrySet())
                    urib.addParameter(entry.getKey(), entry.getValue().toString());
            }
            return urib.build();
        } catch(URISyntaxException e) {
            thrown = e;
            return null;
        }
    }
    
    private void putHeaders(HttpRequest request, Map<String, Object> headers) {
        if(headers != null)
            for(Entry<String, Object> header : headers.entrySet())
                request.addHeader(header.getKey(), header.getValue().toString());
    }
    
    private Map<String, String> extractHeaders(HttpResponse response) {
        if(response == null)
            return new HashMap<String, String>();
        Map<String, String> headers = new HashMap<String, String>();
        for(Header h : response.getAllHeaders())
            headers.put(h.getName(), h.getValue());
        return headers;
    }
    
    private void reset() {
        retried = 0;
        thrown = null;
        errorCallback = null;
        responseCallback = null;
        exceptionCallback = null;
        response = null;
        request = null;
    }
    
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("{\n");
        builder.append("getStagedRequest(): HttpUriRequest, get the request staged by the method call or the last request\n");
        builder.append("setStagedRequest( HttpUriRequest ): void, explicitly stage the next request\n");
        builder.append("getResponse(): HttpResponse, get the most recent response, null if a new request is staging\n");
        builder.append("request( requestObject ): make a whole request with callbacks as request object attributes\n");
        builder.append("get( url, headers, parameters ): set the GET, returns HttpBuilder\n");
        builder.append("post( url, headers, content ): set the POST, returns HttpBuilder\n");
        builder.append("put( url, headers, content ): set the PUT, returns HttpBuilder\n");
        builder.append("delete( url, headers ): set the DELETE, returns HttpBuilder\n");
        builder.append("resp( function( status, headers, content ){} ): set the response callback, returns HttpBuilder\n");
        builder.append("err( function( status, headers, content ){} ): set the error callback, returns HttpBuilder\n");
        builder.append("excp( function( exception ){} ): set the error callback, returns HttpBuilder\n");
        builder.append("execute(): execute the staged request and receive callbacks\n");
        builder.append("retry(): retry the staged request and receive callbacks\n");
        builder.append("getRetried(): int, the number of times the request was attempted to be sent\n");
        builder.append("setOkayStatusArray( int[] ): void, set the status codes to call the resp function on, default [200]\n");
        builder.append("}");
        return builder.toString();
    }
    
    private class ScriptObjectMirrorCallbackWrapper implements ScriptHttpCallback {

        private final JSObject jso; 
        
        ScriptObjectMirrorCallbackWrapper(JSObject jso) {
            this.jso = jso;
        }
        
        @Override
        public Object invoke(int status, Map<String, String> headers, String content) {
            return jso.call(HttpBuilderScriptUtility.this, status, headers, content);
        }
        
    }
}
