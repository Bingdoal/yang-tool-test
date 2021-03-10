import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.opendaylight.yangtools.yang.common.YangConstants;
import org.opendaylight.yangtools.yang.model.api.*;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.parser.api.YangSyntaxErrorException;
import org.opendaylight.yangtools.yang.model.repo.api.StatementParserMode;
import org.opendaylight.yangtools.yang.parser.rfc7950.reactor.RFC7950Reactors;
import org.opendaylight.yangtools.yang.parser.rfc7950.repo.YangStatementStreamSource;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;
import org.opendaylight.yangtools.yang.parser.stmt.reactor.CrossSourceStatementReactor;
import org.opendaylight.yangtools.yang.parser.stmt.reactor.EffectiveSchemaContext;
import schema.ModuleDto;

import java.io.FileFilter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collection;

public class Main {
    public static final FileFilter YANG_FILE_FILTER =
            file -> file.getName().endsWith(YangConstants.RFC6020_YANG_FILE_EXTENSION) && file.isFile();

    public static EffectiveSchemaContext schemaContext;

    public static void main(String[] args) throws IOException, YangSyntaxErrorException, ReactorException, URISyntaxException {
        YangToJson yangToJson = new YangToJson();
        final Collection<YangStatementStreamSource> sources = yangToJson.getSource("");
        final CrossSourceStatementReactor.BuildAction reactor =
                RFC7950Reactors.defaultReactor().newBuild(StatementParserMode.DEFAULT_MODE)
                        .addSources(sources);

        schemaContext = reactor.buildEffective();

        Module module = schemaContext.findModules("ietf-netconf-monitoring").iterator().next();
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new Jdk8Module());
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        ModuleDto moduleDto = yangToJson.convertToDto(schemaContext, module);
        String json = mapper.writeValueAsString(moduleDto);
        System.out.println(json);
//        System.out.println(module.getName());
//        System.out.println("getRpcs:");
//        for (RpcDefinition rpc : module.getRpcs()) {
//            printIndent(1);
//            System.out.println(rpc.getQName().getLocalName());
//            getRpc(rpc, 2);
//        }
//
//        System.out.println("getNotifications:");
//        for (NotificationDefinition notification : module.getNotifications()) {
//            printIndent(1);
//            System.out.println(notification.getQName().getLocalName());
//        }
    }

    public static void getRpc(RpcDefinition rpc, int level) {
        printIndent(level);
        System.out.println("Input:");
        for (DataSchemaNode childNode : rpc.getInput().getChildNodes()) {
            if (childNode instanceof DataNodeContainer) {
                DataTreeDebugger.getContainer(childNode, level + 1);
            } else if (childNode instanceof LeafSchemaNode
                    || childNode instanceof LeafListSchemaNode) {
                DataTreeDebugger.getDataNode(childNode, level + 1);
            }
        }
        printIndent(level);
        System.out.println("Output:");
        for (DataSchemaNode childNode : rpc.getOutput().getChildNodes()) {
            if (childNode instanceof DataNodeContainer) {
                DataTreeDebugger.getContainer(childNode, level + 1);
            } else if (childNode instanceof LeafSchemaNode
                    || childNode instanceof LeafListSchemaNode) {
                DataTreeDebugger.getDataNode(childNode, level + 1);
            }
        }
    }

    public static void printIndent(int number) {
        for (int i = 0; i < number; i++) {
            System.out.print("\t");
        }
    }

}
