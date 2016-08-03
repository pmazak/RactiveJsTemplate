import org.apache.commons.lang.StringEscapeUtils;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * In simple terms the server-side is:

     engine = manager.getEngineByName("nashorn");
     engine.eval(getResourceRaw(“js/ractive.min.js"));
     engine.eval("var ractive = new Ractive({ " +
         "template: '" + templateHtml + "', " +
         "data: JSON.parse('" + jsonString + "'), " +
         "partials: {" + partialsHtml + "}" +
         "})");
     return (String) engine.eval("ractive.toHtml()”);

 * But the particulars of knowing and getting the partials was hard.
 * This was the reason behind this plugin: https://medium.com/behancetech/github-com-behance-ractive-partials-868467ab17c1#.otgmyg86l (https://github.com/behance/ractive-partials)
 */
public class RactiveJsTemplate {
    private ScriptEngineManager manager = new ScriptEngineManager();
    private ScriptEngine engine;

    public RactiveJsTemplate(String ractiveJsLocation) {
        try {
            engine = manager.getEngineByName("nashorn");
            engine.eval(getResourceRaw(ractiveJsLocation));
        } catch (ScriptException e) {
            throw new RuntimeException(e);
        }
    }

    public String toHtml(String template, String jsonString) {
        String templateHtml = getEscapedHtml(template);
        Map<String, String> partials = parsePartials(templateHtml);
        String partialsHtml = getPartialsJs(partials);
        String ractive = "var ractive = new Ractive({ " +
            "template: '" + templateHtml + "', " +
            "data: JSON.parse('" + jsonString + "'), " +
            "partials: {" + partialsHtml + "}" +
            "})";
        System.out.println(ractive);
        try {
            engine.eval(ractive);
            return (String) engine.eval("ractive.toHtml()");
        } catch (ScriptException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private String getPartialsJs(Map<String, String> partials) {
        StringBuilder partialsHtml = new StringBuilder();
        for(Map.Entry<String, String> it : partials.entrySet()) {
            if (partialsHtml.length() > 0)
                partialsHtml.append(", ");
            partialsHtml.append(it.getKey()).append(": '")
                .append(getEscapedHtml(it.getValue()))
                .append("'");
        }
        return partialsHtml.toString();
    }

    static Map<String,String> parsePartials(String templateHtml) {
        Map<String, String> partials = new HashMap<>();
        String pattern = "\\{\\{>\\s?([a-zA-Z0-9_\\.\\/]+)\\s?\\}\\}";
        Matcher matcher = Pattern.compile(pattern).matcher(templateHtml);
        while (matcher.find()) {
            if (matcher.groupCount() > 0) {
                String key = matcher.group(1);
                String filePath = key.replaceAll("_", "/") + ".html";
                partials.put(key, filePath);
            }
        }
        return partials;
    }

    private String getEscapedHtml(String htmlFile) {
        return StringEscapeUtils.escapeJavaScript(getResourceRaw(htmlFile).replaceAll("\n", ""));
    }

    private static String getResourceRaw(String resourceName) {
        Reader reader = null;
        final int bufferSize = 1024;
        try {
            reader = new InputStreamReader(openResourceInputStream(resourceName));
            char[] cbuf = new char[bufferSize];
            StringBuilder sb = new StringBuilder(bufferSize);
            int len;
            while ((len = reader.read(cbuf, 0, bufferSize)) != -1) {
                sb.append(cbuf, 0, len);
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private static InputStream openResourceInputStream(String resourceName) throws Exception {
//        Class type = ResourceBundle.class;
//        Field cacheList = type.getDeclaredField("cacheList");
//        cacheList.setAccessible(true);
//        ((Map)cacheList.get(ResourceBundle.class)).clear();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        URL url = classLoader.getResource(resourceName);
        URLConnection resourceConnection = url.openConnection();
        resourceConnection.setUseCaches(false);
//        resourceConnection.setDefaultUseCaches(false);
        return resourceConnection.getInputStream();
    }
}
