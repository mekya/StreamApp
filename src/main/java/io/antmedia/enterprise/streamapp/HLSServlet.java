package io.antmedia.enterprise.streamapp;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.ServletOutputStream;
import java.util.ArrayList;
import org.apache.catalina.WebResource;
import java.io.FileNotFoundException;
import javax.servlet.DispatcherType;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletRequest;
import org.apache.catalina.servlets.DefaultServlet;

public class HLSServlet extends DefaultServlet
{
    protected void serveResource(final HttpServletRequest request, final HttpServletResponse response, final boolean content, final String encoding) throws IOException, ServletException {
        boolean serveContent = content;
        final String path = this.getRelativePath(request, true);
        if (this.debug > 0) {
            if (serveContent) {
                this.log("DefaultServlet.serveResource:  Serving resource '" + path + "' headers and data");
            }
            else {
                this.log("DefaultServlet.serveResource:  Serving resource '" + path + "' headers only");
            }
        }
        if (path.length() == 0) {
            return;
        }
        final WebResource resource = this.resources.getResource(path);
        final boolean isError = DispatcherType.ERROR == request.getDispatcherType();
        if (!resource.exists()) {
            String requestUri = (String)request.getAttribute("javax.servlet.include.request_uri");
            if (requestUri == null) {
                requestUri = request.getRequestURI();
                if (isError) {
                    response.sendError((int)request.getAttribute("javax.servlet.error.status_code"));
                }
                else {
                    response.sendError(404, requestUri);
                }
                return;
            }
            throw new FileNotFoundException(HLSServlet.sm.getString("defaultServlet.missingResource", new Object[] { requestUri }));
        }
        else {
            if (resource.canRead()) {
                boolean included = false;
                if (resource.isFile()) {
                    included = (request.getAttribute("javax.servlet.include.context_path") != null);
                    if (!included && !isError && !this.checkIfHeaders(request, response, resource)) {
                        return;
                    }
                }
                String contentType = resource.getMimeType();
                if (contentType == null) {
                    contentType = this.getServletContext().getMimeType(resource.getName());
                    resource.setMimeType(contentType);
                }
                String eTag = null;
                String lastModifiedHttp = null;
                if (resource.isFile() && !isError) {
                    eTag = resource.getETag();
                    lastModifiedHttp = resource.getLastModifiedHttp();
                }
                ArrayList<DefaultServlet.Range> ranges = null;
                long contentLength = -1L;
                if (!isError) {
                    if (this.useAcceptRanges) {
                        response.setHeader("Accept-Ranges", "bytes");
                    }
                    ranges = (ArrayList<DefaultServlet.Range>)this.parseRange(request, response, resource);
                    response.setHeader("ETag", eTag);
                    response.setHeader("Last-Modified", lastModifiedHttp);
                }
                contentLength = resource.getContentLength();
                if (contentLength == 0L) {
                    this.log("DefaultServlet.serveFile:  contentlength is zero='" + path + "'");
                    serveContent = false;
                }
                ServletOutputStream ostream = null;
                final PrintWriter writer = null;
                if (serveContent) {
                    try {
                        ostream = response.getOutputStream();
                    }
                    catch (IllegalStateException ex) {}
                }
                if (contentType != null) {
                    if (this.debug > 0) {
                        this.log("DefaultServlet.serveFile:  contentType='" + contentType + "'");
                    }
                    if (response.getContentType() == null) {
                        response.setContentType(contentType);
                    }
                }
                if (serveContent) {
                    try {
                        response.setBufferSize(this.output);
                    }
                    catch (IllegalStateException ex2) {}
                    final String queryString = request.getQueryString();
                    String updatedContent = null;
                    if (queryString != null && !queryString.isEmpty()) {
                        if (resource.getName().endsWith("_adaptive.m3u8")) {
                            updatedContent = new String(resource.getContent()).replaceAll("\\.m3u8", ".m3u8?" + queryString);
                        }
                        else {
                            updatedContent = new String(resource.getContent()).replaceAll("\\.ts", ".ts?" + queryString);
                        }
                    }
                    else {
                        updatedContent = new String(resource.getContent());
                    }
                    response.setContentLengthLong((long)updatedContent.length());
                    ostream.write(updatedContent.getBytes());
                }
                return;
            }
            String requestUri = (String)request.getAttribute("javax.servlet.include.request_uri");
            if (requestUri == null) {
                requestUri = request.getRequestURI();
                if (isError) {
                    response.sendError((int)request.getAttribute("javax.servlet.error.status_code"));
                }
                else {
                    response.sendError(403, requestUri);
                }
                return;
            }
            throw new FileNotFoundException(HLSServlet.sm.getString("defaultServlet.missingResource", new Object[] { requestUri }));
        }
    }
}