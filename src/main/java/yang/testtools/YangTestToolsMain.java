package yang.testtools;

import org.opendaylight.yangtools.yang.model.parser.api.YangSyntaxErrorException;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;
import yang.testtools.yangparser.YangParserUtils;

import java.io.IOException;
import java.net.URISyntaxException;

public class YangTestToolsMain {
    public static void main(String[] args) throws IOException, YangSyntaxErrorException, ReactorException, URISyntaxException, ClassNotFoundException {
//        if (args == null || args.length <= 0) {
//            System.out.println("Need input yang directory path.");
//            return;
//        }
//        YangParserUtils.yangToJsonFile(args);
        YangParserUtils.yangToJsonFile("C:\\affirmed\\nssf");
    }
}
