import helper.YangUtils;
import odlservice.NBIParamDto;
import org.opendaylight.yangtools.yang.model.parser.api.YangSyntaxErrorException;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;
import odlservice.YangJsonToNBI;
import yangparser.YangParserUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

public class Application {


    public static void main(String[] args) throws IOException, YangSyntaxErrorException, ReactorException, URISyntaxException {
        YangUtils.schemaContext = YangUtils.getSchemaContext("/oran");
                YangParserUtils.yangToJsonFile("/oran");
//        String json = YangParserUtils.yangToJsonDebug("/oran", "ietf-hardware");
//        String json = YangParserUtils.yangToJsonDebug("/oran", "o-ran-beamforming");
//        System.out.println(json);

//        ObjectMapper mapper = new ObjectMapper();
//        mapper.registerModule(new Jdk8Module());
//        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
//        ModuleDto moduleDto = mapper.convertValue(json, ModuleDto.class);

//        YangJsonToNBI yangJsonToNBI = new YangJsonToNBI();
//        yangJsonToNBI.getConfig("ietf-interfaces", "interfaces/interface/{name}/ietf-ip:ipv4");
//        NBIParamDto nbiParamDto = new NBIParamDto();
//        nbiParamDto.setPath("interfaces/interface/{name}/ietf-ip:ipv4");
//        nbiParamDto.setKeys(Map.of(
//                "name", "interface-name"
//        ));
//        yangJsonToNBI.postConfig("ietf-interfaces", nbiParamDto);
    }


}
