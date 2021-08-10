import org.opendaylight.yangtools.yang.model.parser.api.YangSyntaxErrorException;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;
import yangparser.YangParserUtils;

import java.io.IOException;
import java.net.URISyntaxException;

public class Application {


    public static void main(String[] args) throws IOException, YangSyntaxErrorException, ReactorException, URISyntaxException, ClassNotFoundException {
        YangParserUtils.yangToJsonFile();
    }
}
