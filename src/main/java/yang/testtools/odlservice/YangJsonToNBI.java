package yang.testtools.odlservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import yang.testtools.helper.YangUtils;

public class YangJsonToNBI {
    private String protocol = "http://";
    private String host = "60.251.156.216";
    private int port = 48181;
    private String baseUrl;
    private String nodeName = "testt";
    private String topologyPrefix = "/network-topology:network-topology/topology/topology-netconf/node/";

    public YangJsonToNBI() {
        this.baseUrl = this.protocol + this.host + ":" + this.port + "/restconf/";
    }

    public void postConfig(String moduleName, NBIParamDto paramDto) {
        String ns = YangUtils.getNamespace(moduleName);
        String path = paramDto.getPath();
        if (paramDto.getKeys() != null && !paramDto.getKeys().isEmpty()) {
            for (String name : paramDto.getKeys().keySet()) {
                path = path.replace("{" + name + "}", paramDto.getKeys().get(name));
            }
        }
        String postConfigUrl = this.baseUrl + "config" + topologyPrefix + nodeName +
                "/yang-ext:mount/" + moduleName + ":" + path;
        System.out.println("POST: " + postConfigUrl);
        System.out.println("request body: " + paramDto.getContent());
    }

    public void putConfig(String moduleName, NBIParamDto paramDto) {
        String ns = YangUtils.getNamespace(moduleName);
    }

    public void deleteConfig(String moduleName, String path) {
        String ns = YangUtils.getNamespace(moduleName);
    }

    public void getConfig(String moduleName, String path) {
        String getConfigUrl = this.baseUrl + "operations" + topologyPrefix + nodeName + "/yang-ext:mount/ietf-netconf:get";
        ObjectNode body = new ObjectMapper().createObjectNode();
        ObjectNode input = new ObjectMapper().createObjectNode();

        String ns = YangUtils.getNamespace(moduleName);
        String filterXml = FilterParser.xmlParser(ns, path);
        input.put("ietf-netconf:filter", filterXml);
        input.put("ietf-netconf:ietf-netconf-with-defaults:with-defaults", "report-all");
        body.set("ietf-netconf:input", input);

        System.out.println("POST: " + getConfigUrl);
        System.out.println("request body: " + body.toString());
    }

    public void execRpc(String moduleName, String rpc, JsonNode input) {
        String ns = YangUtils.getNamespace(moduleName);
    }

    public void subscriptionNotification(String moduleName, String notification) {
        String ns = YangUtils.getNamespace(moduleName);
    }
}
