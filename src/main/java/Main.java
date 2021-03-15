import org.opendaylight.yangtools.yang.model.parser.api.YangSyntaxErrorException;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;
import yangparser.YangParserUtils;

import java.io.IOException;
import java.net.URISyntaxException;

public class Main {

    public static void main(String[] args) throws IOException, YangSyntaxErrorException, ReactorException, URISyntaxException {
        YangParserUtils.yangToJsonFile("/oran");
//        String json = YangParserUtils.yangToJsonDebug("/oran", "ietf-hardware");
//        System.out.println(json);
    }


}
