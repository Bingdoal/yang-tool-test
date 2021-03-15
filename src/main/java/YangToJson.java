import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.google.common.collect.Range;
import org.opendaylight.yangtools.yang.common.YangConstants;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.*;
import org.opendaylight.yangtools.yang.model.api.type.*;
import org.opendaylight.yangtools.yang.model.parser.api.YangSyntaxErrorException;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.parser.rfc7950.repo.YangStatementStreamSource;
import org.opendaylight.yangtools.yang.parser.stmt.reactor.EffectiveSchemaContext;
import schema.*;
import schema.type.BitsType;
import schema.type.TypeProperty;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;

public class YangToJson {
    public final FileFilter YANG_FILE_FILTER =
            file -> file.getName().endsWith(YangConstants.RFC6020_YANG_FILE_EXTENSION) && file.isFile();
    public EffectiveSchemaContext schemaContext;
    private Module module;
    private ObjectMapper mapper;

    public YangToJson() {
        mapper = new ObjectMapper();
        mapper.registerModule(new Jdk8Module());
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    public Collection<YangStatementStreamSource> getSource(String path) throws URISyntaxException, IOException, YangSyntaxErrorException {
        File sourcesDir = new File(Main.class.getResource(path).toURI());
        File[] files = sourcesDir.listFiles(YANG_FILE_FILTER);
        Collection<YangStatementStreamSource> sources = new ArrayList<>(files.length);
        for (File file : files) {
            sources.add(YangStatementStreamSource.create(YangTextSchemaSource.forFile(file)));
        }
        return sources;
    }

    public void convertToDto(EffectiveSchemaContext schemaContext) {
        for (Module module : schemaContext.getModules()) {
            System.out.println("Module: " + module.getName());
            ModuleDto moduleDto = convertToDto(schemaContext, module);
            try {
                String json = mapper.writeValueAsString(moduleDto);
                try {
                    Path destination;
                    if (moduleDto.isEmpty()) {
                        destination = Paths.get("./parser_result/empty/" + module.getName() + ".json");
                    } else {
                        destination = Paths.get("./parser_result/" + module.getName() + ".json");
                    }
                    Files.writeString(destination, json, StandardCharsets.UTF_8, WRITE, CREATE);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                System.out.println(json);

            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
        }
    }

    public ModuleDto convertToDto(EffectiveSchemaContext schemaContext, Module module) {
        this.schemaContext = schemaContext;
        this.module = module;

        ModuleDto moduleDto = new ModuleDto();
        moduleDto.setName(module.getName());
        moduleDto.setNamespace(module.getNamespace().toString());
        if (module.getRevision().isPresent()) {
            moduleDto.setRevision(module.getRevision().get().toString());
        }

        DataTreeDto dataTreeDto = getDataTree(module.getChildNodes(), true);
        if (!dataTreeDto.isEmpty()) {
            moduleDto.setDataTree(Optional.of(dataTreeDto));
        }

        Map<String, RpcDto> rpcTree = getRpcTree(module.getRpcs());
        if (!rpcTree.isEmpty()) {
            moduleDto.setRpc(Optional.of(rpcTree));
        }

        Map<String, ContainerDto> notificationTree = getNotificationTree(module.getNotifications());
        if (!notificationTree.isEmpty()) {
            moduleDto.setNotification(Optional.of(notificationTree));
        }
        return moduleDto;
    }

    private Map<String, ContainerDto> getNotificationTree(Collection<? extends NotificationDefinition> notifications) {
        Map<String, ContainerDto> notificationTree = new HashMap<>();
        for (NotificationDefinition notification : notifications) {
            ContainerDto containerDto = getContainer(notification, true);
            notificationTree.put(notification.getQName().getLocalName(), containerDto);
        }
        return notificationTree;
    }

    private Map<String, RpcDto> getRpcTree(Collection<? extends RpcDefinition> rpcs) {
        Map<String, RpcDto> rpcTree = new HashMap<>();
        for (RpcDefinition rpc : rpcs) {
            RpcDto rpcDto = new RpcDto();
            ContainerDto input = getContainer(rpc.getInput(), true);
            ContainerDto output = getContainer(rpc.getOutput(), true);

            if (!input.isEmpty()) {
                rpcDto.setInput(Optional.of(input));
            }
            if (!output.isEmpty()) {
                rpcDto.setOutput(Optional.of(output));
            }
            rpcTree.put(rpc.getQName().getLocalName(), rpcDto);
        }
        return rpcTree;
    }


    private DataTreeDto getDataTree(Collection<? extends DataSchemaNode> childNodes, boolean defaultConfig) {
        DataTreeDto dataTreeDto = new DataTreeDto();
        Map<String, LeafDto> leaves = new HashMap<>();
        Map<String, ContainerDto> containers = new HashMap<>();
        Map<String, LeafDto> leafList = new HashMap<>();
        Map<String, ContainerDto> containerList = new HashMap<>();
        for (DataSchemaNode node : childNodes) {
            if (node.getStatus() == Status.DEPRECATED) {
                continue;
            }
            boolean config = node.effectiveConfig().isPresent() ? node.effectiveConfig().get() : defaultConfig;

            String nodeName = node.getQName().getLocalName();
            if (node.isAugmenting()) {
                String moduleName = schemaContext.findModules(node.getQName().getNamespace())
                        .iterator().next().getName();
                nodeName = moduleName + ":" + nodeName;
            }

            if (node instanceof DataNodeContainer) {
                ContainerDto tmpContainer = getContainer((DataNodeContainer) node, config);
                if (tmpContainer.isArray()) {
                    containerList.put(nodeName, tmpContainer);
                } else {
                    containers.put(nodeName, tmpContainer);
                }
            } else if (isLeafLikeNode(node)) {
                LeafDto tmpLeaf = getDataNode(node, config);
                if (tmpLeaf.isArray()) {
                    leafList.put(nodeName, tmpLeaf);
                } else {
                    leaves.put(nodeName, tmpLeaf);
                }
            }

        }
        if (!leaves.isEmpty()) {
            dataTreeDto.setLeaf(Optional.of(leaves));
        }
        if (!containers.isEmpty()) {
            dataTreeDto.setContainer(Optional.of(containers));
        }
        if (!leafList.isEmpty()) {
            dataTreeDto.setLeafList(Optional.of(leafList));
        }
        if (!containerList.isEmpty()) {
            dataTreeDto.setList(Optional.of(containerList));
        }
        return dataTreeDto;
    }

    private ContainerDto getContainer(DataNodeContainer dataNodeContainer, boolean defaultConfig) {

        ContainerDto containerDto = new ContainerDto();
        containerDto.setConfig(defaultConfig);

        if (dataNodeContainer instanceof DataSchemaNode) {
            DataSchemaNode childNode = (DataSchemaNode) dataNodeContainer;
            if (childNode.effectiveConfig().isPresent()) {
                containerDto.setConfig(childNode.effectiveConfig().get());
            }
            if (childNode instanceof ListSchemaNode) {
                ListSchemaNode listSchemaNode = (ListSchemaNode) childNode;
                containerDto.setArray(true);
                if (listSchemaNode.getKeyDefinition().size() > 0) {
                    containerDto.setKey(Optional.of(listSchemaNode.getKeyDefinition().get(0).getLocalName()));
                }
            }
        }

        DataTreeDto dataTreeDto = getDataTree(dataNodeContainer.getChildNodes(), defaultConfig);

        if (dataTreeDto.getLeaf() != null && dataTreeDto.getLeaf().isPresent()) {
            containerDto.setLeaf(dataTreeDto.getLeaf());
        }
        if (dataTreeDto.getContainer() != null && dataTreeDto.getContainer().isPresent()) {
            containerDto.setContainer(dataTreeDto.getContainer());
        }
        if (dataTreeDto.getLeafList() != null && dataTreeDto.getLeafList().isPresent()) {
            containerDto.setLeafList(dataTreeDto.getLeafList());
        }
        if (dataTreeDto.getList() != null && dataTreeDto.getList().isPresent()) {
            containerDto.setList(dataTreeDto.getList());
        }
        return containerDto;
    }

    private boolean isLeafLikeNode(DataSchemaNode node) {
        return node instanceof LeafSchemaNode
                || node instanceof LeafListSchemaNode
                || node instanceof AnydataSchemaNode
                || node instanceof AnyxmlSchemaNode
                || node instanceof ChoiceSchemaNode;
    }

    private LeafDto getDataNode(DataSchemaNode childNode, boolean defaultConfig) {
        LeafDto leafDto = new LeafDto();
        leafDto.setConfig(defaultConfig);
        if (childNode.effectiveConfig().isPresent()) {
            leafDto.setConfig(childNode.effectiveConfig().get());
        }
        if (childNode instanceof MandatoryAware) {
            MandatoryAware mandatoryAware = (MandatoryAware) childNode;
            leafDto.setMandatory(mandatoryAware.isMandatory());
        }

        if (childNode instanceof TypedDataSchemaNode) {
            TypedDataSchemaNode typedData = (TypedDataSchemaNode) childNode;
            TypeProperty type = getTypeInfo(typedData.getType());
            leafDto.setType(type.getName());
            leafDto.setTypeProperty(type);
        }

        if (childNode instanceof LeafListSchemaNode) {
            leafDto.setArray(true);
        } else if (childNode instanceof AnydataSchemaNode) {
            AnydataSchemaNode anydata = (AnydataSchemaNode) childNode;
            leafDto.setType("anydata");
            leafDto.setTypeProperty(new TypeProperty("anydata"));
        } else if (childNode instanceof AnyxmlSchemaNode) {
            AnyxmlSchemaNode anyxml = (AnyxmlSchemaNode) childNode;
            leafDto.setType("anyxml");
            leafDto.setTypeProperty(new TypeProperty("anyxml"));
        } else if (childNode instanceof ChoiceSchemaNode) {
            TypeProperty type = getChoiceTypeInfo((ChoiceSchemaNode) childNode);
            leafDto.setType(type.getName());
            leafDto.setTypeProperty(type);
        } else if (!(childNode instanceof LeafSchemaNode)) {
            System.out.println("Unknow Node: " + childNode.getQName().getLocalName());
        }
        return leafDto;
    }

    private TypeProperty getChoiceTypeInfo(ChoiceSchemaNode choiceNode) {
        TypeProperty typeProperty = new TypeProperty();
        typeProperty.setName("choice");
        if (choiceNode.getDefaultCase().isPresent()) {
            typeProperty.setDefaultCase(Optional.of(choiceNode.getDefaultCase().get().getQName().getLocalName()));
        }

        Map<String, DataTreeDto> cases = new HashMap<>();
        for (CaseSchemaNode caseNode : choiceNode.getCases()) {
            cases.put(caseNode.getQName().getLocalName(), getDataTree(caseNode.getChildNodes(), true));
        }

        typeProperty.setCases(Optional.of(cases));
        return typeProperty;
    }

    private TypeProperty getTypeInfo(TypeDefinition<? extends TypeDefinition<?>> nodeType) {
        TypeProperty typeProperty = new TypeProperty();
        TypeDefinition<? extends TypeDefinition<?>> typeDefinition = nodeType;
        String typeName = "";
        if (nodeType.getBaseType() != null) {
            typeName = nodeType.getBaseType().getQName().getLocalName();
        } else {
            typeName = nodeType.getQName().getLocalName();
        }
        typeProperty.setName(typeName);

        if (nodeType.getDefaultValue().isPresent()) {
            typeProperty.setDefaultValue((Optional<String>) (nodeType.getDefaultValue()));
        }

        if (typeDefinition instanceof RangeRestrictedTypeDefinition) {
            RangeRestrictedTypeDefinition rangeRestrictedType = (RangeRestrictedTypeDefinition) typeDefinition;
            Optional<RangeConstraint> rangeConstraint = rangeRestrictedType.getRangeConstraint();
            if (rangeConstraint.isPresent()) {
                Range range = rangeConstraint.get().getAllowedRanges().span();
                typeProperty.setRange(Optional.of(range.lowerEndpoint() + "~" + range.upperEndpoint()));
            }
        }

        if (typeDefinition instanceof LengthRestrictedTypeDefinition) {
            LengthRestrictedTypeDefinition lengthRestrictedType =
                    (LengthRestrictedTypeDefinition) typeDefinition;
            if (lengthRestrictedType.getLengthConstraint().isPresent()) {
                LengthConstraint lengthConstraint = (LengthConstraint) lengthRestrictedType.getLengthConstraint().get();
                Range<Integer> span = lengthConstraint.getAllowedRanges().span();
                String lengthRange = span.lowerEndpoint() + "~" + span.upperEndpoint();
                typeProperty.setLength(Optional.of(lengthRange));
            }
        }

        if (typeDefinition instanceof EnumTypeDefinition) {
            EnumTypeDefinition enumType = (EnumTypeDefinition) typeDefinition;
            List<String> options = new ArrayList<>();
            for (EnumTypeDefinition.EnumPair value : enumType.getValues()) {
                options.add(value.getValue() + ":" + value.getName());
            }
            typeProperty.setOptions(Optional.of(options));

        } else if (typeDefinition instanceof IdentityrefTypeDefinition) {
            IdentityrefTypeDefinition identityrefType = (IdentityrefTypeDefinition) typeDefinition;
            typeProperty.setOptions(Optional.of(getIdentityOptions(identityrefType.getIdentities())));

        } else if (typeDefinition instanceof LeafrefTypeDefinition) {
            LeafrefTypeDefinition leafrefType = (LeafrefTypeDefinition) typeDefinition;
            typeProperty.setRequireInstance(Optional.of(leafrefType.requireInstance()));
            typeProperty.setPath(Optional.of(leafrefType.getPathStatement().getOriginalString()));

        } else if (typeDefinition instanceof DecimalTypeDefinition) {
            DecimalTypeDefinition decimalType = (DecimalTypeDefinition) typeDefinition;
            typeProperty.setFractionDigits(Optional.of(decimalType.getFractionDigits()));

        } else if (typeDefinition instanceof StringTypeDefinition) {
            StringTypeDefinition stringType = (StringTypeDefinition) typeDefinition;
            List<String> patterns = new ArrayList<>();
            for (PatternConstraint patternConstraint : stringType.getPatternConstraints()) {
                patterns.add(patternConstraint.getRegularExpressionString());
            }
            if (patterns.size() > 0) {
                typeProperty.setPattern(Optional.of(patterns));
            }
        } else if (typeDefinition instanceof BitsTypeDefinition) {
            BitsTypeDefinition bitsType = (BitsTypeDefinition) typeDefinition;
            List<BitsType> bitsTypes = new ArrayList<>();
            for (BitsTypeDefinition.Bit bit : bitsType.getBits()) {
                BitsType bitT = new BitsType();
                bitT.setName(bit.getName());
                bitT.setPosition(Integer.parseInt(bit.getPosition().toString()));
                bitT.setDescription(bit.getDescription());
            }
            typeProperty.setBits(Optional.of(bitsTypes));

        } else if (typeDefinition instanceof UnionTypeDefinition) {
            UnionTypeDefinition unionType = (UnionTypeDefinition) typeDefinition;
            List<TypeProperty> types = new ArrayList<>();
            for (TypeDefinition<?> type : unionType.getTypes()) {
                TypeProperty tmp = getTypeInfo(type);
                types.add(tmp);
            }
            typeProperty.setUnionTypes(Optional.of(types));

        } else if (typeDefinition instanceof InstanceIdentifierTypeDefinition) {
            InstanceIdentifierTypeDefinition instanceIdentifierType =
                    (InstanceIdentifierTypeDefinition) typeDefinition;
            typeProperty.setRequireInstance(Optional.of(instanceIdentifierType.requireInstance()));

        } else if (!(typeDefinition instanceof RangeRestrictedTypeDefinition
                || typeDefinition instanceof BooleanTypeDefinition
                || typeDefinition instanceof EmptyTypeDefinition)) {
            System.out.println("Unknow type: " + typeDefinition.getQName().getLocalName());
        }
        return typeProperty;
    }

    private List<String> getIdentityOptions(Collection<? extends IdentitySchemaNode> identities) {
        List<String> options = new ArrayList<>();
        for (IdentitySchemaNode identity : identities) {
            options.add(identity.getQName().getLocalName());
            options.addAll(getDerivedIdentities(identity));
        }
        return options;
    }

    private List<String> getDerivedIdentities(IdentitySchemaNode identity) {
        List<String> options = new ArrayList<>();
        for (IdentitySchemaNode derivedIdentity : schemaContext.getDerivedIdentities(identity)) {
            options.add(derivedIdentity.getQName().getLocalName());
            if (schemaContext.getDerivedIdentities(derivedIdentity).size() > 0) {
                options.addAll(getDerivedIdentities(derivedIdentity));
            }
        }
        return options;
    }
}
