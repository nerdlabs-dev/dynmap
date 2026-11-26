package org.dynmap.servlet;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.Map;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * nerdlabs: lets the lobby serve one map page for the whole network.
 *
 * /net/&lt;server&gt;/anything is fetched from http://127.0.0.1:&lt;port&gt;/anything, where &lt;port&gt; is that
 * server's Dynmap web port from the lobby's config.yml (network: servers:). Only listed servers are
 * reachable and only GET is passed through, so this can't be used to reach anything else.
 */
public class NetworkProxyServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String[] COPY_REQUEST = { "If-Modified-Since", "If-None-Match" };
    private static final String[] COPY_RESPONSE = { "Content-Type", "Last-Modified", "ETag", "Cache-Control", "Expires" };

    private final Map<String, Integer> ports; // lower-case server name -> port

    public NetworkProxyServlet(Map<String, Integer> ports) {
        this.ports = ports;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // Use the raw (still URL-encoded) path so names with spaces etc. pass through unchanged.
        String uri = req.getRequestURI();
        int at = uri.indexOf("/net/");
        if (at < 0) { resp.sendError(404); return; }
        String p = uri.substring(at + 5);
        int slash = p.indexOf('/');
        if (slash <= 0) { resp.sendError(404); return; }
        String name = p.substring(0, slash).toLowerCase(Locale.ROOT);
        String rest = p.substring(slash);
        Integer port = ports.get(name);
        if (port == null) { resp.sendError(404, "Unknown server"); return; }
        if (rest.contains("..") || rest.contains("%2e%2e") || rest.contains("%2E%2E")) { resp.sendError(400); return; }

        String q = req.getQueryString();
        HttpURLConnection c = null;
        try {
            URL u = new URL("http://127.0.0.1:" + port + rest + (q != null ? "?" + q : ""));
            c = (HttpURLConnection) u.openConnection();
            c.setConnectTimeout(3000);
            c.setReadTimeout(15000);
            c.setInstanceFollowRedirects(false);
            for (String h : COPY_REQUEST) {
                String v = req.getHeader(h);
                if (v != null) c.setRequestProperty(h, v);
            }
            int code = c.getResponseCode();
            resp.setStatus(code);
            for (String h : COPY_RESPONSE) {
                String v = c.getHeaderField(h);
                if (v != null) resp.setHeader(h, v);
            }
            InputStream in = (code >= 400) ? c.getErrorStream() : (code == 304 ? null : c.getInputStream());
            if (in != null) {
                try {
                    OutputStream out = resp.getOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                } finally {
                    in.close();
                }
            }
        } catch (IOException e) {
            // That server is stopped or still starting.
            if (!resp.isCommitted()) resp.sendError(502, "Map server '" + name + "' is offline");
        } finally {
            if (c != null) c.disconnect();
        }
    }
}
