package yangparser;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.google.common.collect.Range;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.YangConstants;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.*;
import org.opendaylight.yangtools.yang.model.api.type.*;
import org.opendaylight.yangtools.yang.model.parser.api.YangSyntaxErrorException;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.parser.rfc7950.repo.YangStatementStreamSource;
import org.opendaylight.yangtools.yang.parser.stmt.reactor.EffectiveSchemaContext;
import yangparser.schema.*;
import yangparser.schema.type.BitsType;
import yangparser.schema.type.EnumType;
import yangparser.schema.type.IdentityType;
import yangparser.schema.type.TypeProperty;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
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
        URL url = this.getClass().getResource(path);
        if (url == null) {
            url = this.getClass().getClassLoader().getResource(path);
        }
        File sourcesDir = new File(url.toURI());
        File[] files = sourcesDir.listFiles(YANG_FILE_FILTER);
        Collection<YangStatementStreamSource> sources = new ArrayList<>(files.length);
        for (File file : files) {
            sources.add(YangStatementStreamSource.create(YangTextSchemaSource.forFile(file)));
        }
        return sources;
    }

    public void convertToDto(EffectiveSchemaContext schemaContext) {
        for (Module module : schemaContext.getModules()) {
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
        if (module.getDescription().isPresent()) {
            moduleDto.setDescription(module.getDescription());
        }
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
            moduleDto.setNotification(notificationTree);
        }
        return moduleDto;
    }

    private Map<String, ContainerDto> getNotificationTree(Collection<? extends NotificationDefinition> notifications) {
        Map<String, ContainerDto> notificationTree = new HashMap<>();
        for (NotificationDefinition notification : notifications) {
            ContainerDto containerDto = getContainer(notification, true);
            if (notification.getDescription().isPresent()) {
                containerDto.setDescription(notification.getDescription());
            }
            notificationTree.put(notification.getQName().getLocalName(), containerDto);
        }
        return notificationTree;
    }

    private Map<String, RpcDto> getRpcTree(Collection<? extends RpcDefinition> rpcs) {
        Map<String, RpcDto> rpcTree = new HashMap<>();
        for (RpcDefinition rpc : rpcs) {
            RpcDto rpcDto = new RpcDto();
            if (rpc.getDescription().isPresent()) {
                rpcDto.setDescription(rpc.getDescription());
            }

            ContainerDto input = getContainer(rpc.getInput(), true);
            ContainerDto output = getContainer(rpc.getOutput(), true);
            if (rpc.getInput().getDescription().isPresent()) {
                input.setDescription(rpc.getInput().getDescription());
            }
            if (!input.isEmpty()) {
                rpcDto.setInput(input);
            }

            if (rpc.getOutput().getDescription().isPresent()) {
                output.setDescription(rpc.getOutput().getDescription());
            }
            if (!output.isEmpty()) {
                rpcDto.setOutput(output);
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
            if (childNode.getDescription().isPresent()) {
                containerDto.setDescription(childNode.getDescription());
            }
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
            containerDto.setPath(getPath(childNode));
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

    private String getPath(DataSchemaNode childNode) {
        System.out.println(childNode.getQName().getLocalName());
        String path = "";
        List<QName> pathName = new ArrayList<>();
        for (QName qName : childNode.getPath().getPathFromRoot()) {
            pathName.add(qName);
            path += qName.getLocalName() + "/";
            Optional<DataSchemaNode> dataSchemaNodeOptional = module.findDataTreeChild(pathName);
            if (dataSchemaNodeOptional.isPresent()) {
                DataSchemaNode dataSchemaNode = dataSchemaNodeOptional.get();
                if (dataSchemaNode instanceof ListSchemaNode) {
                    ListSchemaNode listSchemaNode = (ListSchemaNode) dataSchemaNode;
                    if (listSchemaNode.getKeyDefinition().size() > 0) {
                        path += "{" + listSchemaNode.getKeyDefinition().get(0).getLocalName() + "}/";
                    }
                }
            } else {
                return null;
            }
        }
        System.out.println(path);
        return path;
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
        if (childNode.getDescription().isPresent()) {
            leafDto.setDescription(childNode.getDescription());
        }
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
            TypeProperty type = getChoiceTypeInfo((ChoiceSchemaNode) childNode, leafDto.isConfig());
            leafDto.setType(type.getName());
            leafDto.setTypeProperty(type);

        } else if (!(childNode instanceof LeafSchemaNode)) {
            System.out.println("Unknow Node: " + childNode.getQName().getLocalName());
        }
        return leafDto;
    }

    private TypeProperty getChoiceTypeInfo(ChoiceSchemaNode choiceNode, boolean defaultConfig) {
        TypeProperty typeProperty = new TypeProperty();
        typeProperty.setName("choice");
        if (choiceNode.getDefaultCase().isPresent()) {
            typeProperty.setDefaultCase(Optional.of(choiceNode.getDefaultCase().get().getQName().getLocalName()));
        }

        Map<String, DataTreeDto> cases = new HashMap<>();
        for (CaseSchemaNode caseNode : choiceNode.getCases()) {
            cases.put(caseNode.getQName().getLocalName(), getDataTree(caseNode.getChildNodes(), defaultConfig));
        }

        typeProperty.setCases(Optional.of(cases));
        return typeProperty;
    }

    private TypeProperty getTypeInfo(TypeDefinition<? extends TypeDefinition<?>> nodeType) {
        TypeProperty typeProperty = new TypeProperty();

        String typeName = null;
        // base type
        String baseName = nodeType.getQName().getLocalName();

        List<String> basePatterns = new ArrayList<>();
        Range baseRange = null;
        Range baseLength = null;

        TypeDefinition<? extends TypeDefinition<?>> tmpType = nodeType;
        while (!baseName.equals(typeName)) {
            typeName = baseName;
            if (tmpType.getBaseType() != null) {
                baseName = tmpType.getBaseType().getQName().getLocalName();
                tmpType = tmpType.getBaseType();
                if (tmpType instanceof StringTypeDefinition) {
                    StringTypeDefinition stringType = (StringTypeDefinition) tmpType;
                    List<String> patterns = new ArrayList<>();
                    for (PatternConstraint patternConstraint : stringType.getPatternConstraints()) {
                        patterns.add(patternConstraint.getRegularExpressionString());
                    }
                    basePatterns.addAll(patterns);
                }
                if (tmpType instanceof RangeRestrictedTypeDefinition) {
                    RangeRestrictedTypeDefinition rangeType = (RangeRestrictedTypeDefinition) tmpType;
                    RangeConstraint rangeConstraint = (RangeConstraint) rangeType.getRangeConstraint().get();
                    Range range = rangeConstraint.getAllowedRanges().span();
                    if (baseRange == null
                            || baseRange.lowerEndpoint().compareTo(range.lowerEndpoint()) < 0
                            || baseRange.upperEndpoint().compareTo(range.upperEndpoint()) > 0) {
                        baseRange = range;
                    }
                }

                if (nodeType instanceof LengthRestrictedTypeDefinition) {
                    LengthRestrictedTypeDefinition lengthType = (LengthRestrictedTypeDefinition) tmpType;
                    if (lengthType.getLengthConstraint().isPresent()) {
                        LengthConstraint lengthConstraint = (LengthConstraint) lengthType.getLengthConstraint().get();
                        Range range = lengthConstraint.getAllowedRanges().span();
                        if (baseLength == null
                                || baseLength.lowerEndpoint().compareTo(range.lowerEndpoint()) < 0
                                || baseLength.upperEndpoint().compareTo(range.upperEndpoint()) > 0) {
                            baseLength = range;
                        }
                    }
                }
            }
        }
        typeProperty.setName(typeName);

        // current type
        if (nodeType.getDescription().isPresent()) {
            typeProperty.setDescription(nodeType.getDescription());
        }
        if (nodeType.getDefaultValue().isPresent()) {
            typeProperty.setDefaultValue((Optional<String>) (nodeType.getDefaultValue()));
        }
        if (nodeType.getUnits().isPresent()) {
            typeProperty.setUnits(nodeType.getUnits());
        }

        if (nodeType instanceof RangeRestrictedTypeDefinition) {
            RangeRestrictedTypeDefinition rangeType = (RangeRestrictedTypeDefinition) nodeType;
            RangeConstraint rangeConstraint = (RangeConstraint) rangeType.getRangeConstraint().get();
            Range range = rangeConstraint.getAllowedRanges().span();
            if (baseRange == null
                    || baseRange.lowerEndpoint().compareTo(range.lowerEndpoint()) < 0
                    || baseRange.upperEndpoint().compareTo(range.upperEndpoint()) > 0) {
                baseRange = range;
            }
            typeProperty.setRange(Optional.of(baseRange.lowerEndpoint() + "~" + baseRange.upperEndpoint()));
        }

        if (nodeType instanceof LengthRestrictedTypeDefinition) {
            LengthRestrictedTypeDefinition lengthType = (LengthRestrictedTypeDefinition) tmpType;
            if (lengthType.getLengthConstraint().isPresent()) {
                LengthConstraint lengthConstraint = (LengthConstraint) lengthType.getLengthConstraint().get();
                Range range = lengthConstraint.getAllowedRanges().span();
                if (baseLength == null
                        || baseLength.lowerEndpoint().compareTo(range.lowerEndpoint()) < 0
                        || baseLength.upperEndpoint().compareTo(range.upperEndpoint()) > 0) {
                    baseLength = range;
                }
            }
            if (baseLength != null) {
                String lengthRange = baseLength.lowerEndpoint() + "~" + baseLength.upperEndpoint();
                typeProperty.setLength(Optional.of(lengthRange));
            }
        }

        if (nodeType instanceof EnumTypeDefinition) {
            EnumTypeDefinition enumType = (EnumTypeDefinition) nodeType;
            List<EnumType> enumTypes = new ArrayList<>();
            for (EnumTypeDefinition.EnumPair value : enumType.getValues()) {
                EnumType enumT = new EnumType(value.getName(), value.getValue());
                if (value.getDescription().isPresent()) {
                    enumT.setDescription(value.getDescription());
                }
                enumTypes.add(enumT);
            }
            typeProperty.setOptions(Optional.of(enumTypes));

        } else if (nodeType instanceof IdentityrefTypeDefinition) {
            IdentityrefTypeDefinition identityrefType = (IdentityrefTypeDefinition) nodeType;
            IdentitySchemaNode baseIdentity = identityrefType.getIdentities().iterator().next();
            typeProperty.setIdentities(Optional.of(getIdentityOptions(baseIdentity)));
            typeProperty.setBase(Optional.of(baseIdentity.getQName().getLocalName()));

        } else if (nodeType instanceof LeafrefTypeDefinition) {
            LeafrefTypeDefinition leafrefType = (LeafrefTypeDefinition) nodeType;
            typeProperty.setRequireInstance(Optional.of(leafrefType.requireInstance()));
            typeProperty.setPath(Optional.of(leafrefType.getPathStatement().getOriginalString()));

        } else if (nodeType instanceof DecimalTypeDefinition) {
            DecimalTypeDefinition decimalType = (DecimalTypeDefinition) nodeType;
            typeProperty.setFractionDigits(Optional.of(decimalType.getFractionDigits()));

        } else if (nodeType instanceof StringTypeDefinition) {
            StringTypeDefinition stringType = (StringTypeDefinition) nodeType;
            for (PatternConstraint patternConstraint : stringType.getPatternConstraints()) {
                basePatterns.add(patternConstraint.getRegularExpressionString());
            }
            if (basePatterns.size() > 0) {
                typeProperty.setPattern(Optional.of(basePatterns));
            }
        } else if (nodeType instanceof BitsTypeDefinition) {
            BitsTypeDefinition bitsType = (BitsTypeDefinition) nodeType;
            List<BitsType> bitsTypes = new ArrayList<>();
            for (BitsTypeDefinition.Bit bit : bitsType.getBits()) {
                BitsType bitT = new BitsType();
                bitT.setName(bit.getName());
                bitT.setPosition(Integer.parseInt(bit.getPosition().toString()));
                bitT.setDescription(bit.getDescription());
            }
            typeProperty.setBits(Optional.of(bitsTypes));

        } else if (nodeType instanceof UnionTypeDefinition) {
            UnionTypeDefinition unionType = (UnionTypeDefinition) nodeType;
            List<TypeProperty> types = new ArrayList<>();
            for (TypeDefinition<?> type : unionType.getTypes()) {
                TypeProperty tmp = getTypeInfo(type);
                types.add(tmp);
            }
            typeProperty.setUnionTypes(Optional.of(types));

        } else if (nodeType instanceof InstanceIdentifierTypeDefinition) {
            InstanceIdentifierTypeDefinition instanceIdentifierType =
                    (InstanceIdentifierTypeDefinition) nodeType;
            typeProperty.setRequireInstance(Optional.of(instanceIdentifierType.requireInstance()));

        } else if (!(nodeType instanceof BooleanTypeDefinition
                || nodeType instanceof EmptyTypeDefinition
                || nodeType instanceof BinaryTypeDefinition
                || nodeType instanceof RangeRestrictedTypeDefinition)) {
            System.out.println("Unknow type: " + nodeType.getQName().getLocalName());
        }
        return typeProperty;
    }

    private List<IdentityType> getIdentityOptions(IdentitySchemaNode baseIdentity) {
        List<IdentityType> options = new ArrayList<>();
        IdentityType identityType = new IdentityType();
        if (baseIdentity.getDescription().isPresent()) {
            identityType.setDescription(baseIdentity.getDescription());
        }
        identityType.setIdentity(baseIdentity.getQName().getLocalName());
        options.add(identityType);
        options.addAll(getDerivedIdentities(baseIdentity));
        return options;
    }

    private List<IdentityType> getDerivedIdentities(IdentitySchemaNode identity) {
        List<IdentityType> options = new ArrayList<>();
        for (IdentitySchemaNode derivedIdentity : schemaContext.getDerivedIdentities(identity)) {
            Module module = schemaContext.findModules(derivedIdentity.getQName().getNamespace()).iterator().next();

            IdentityType identityType = new IdentityType();
            if (derivedIdentity.getDescription().isPresent()) {
                identityType.setDescription(derivedIdentity.getDescription());
            }
            identityType.setIdentity(module.getName() + ":" + derivedIdentity.getQName().getLocalName());
            options.add(identityType);

            if (schemaContext.getDerivedIdentities(derivedIdentity).size() > 0) {
                options.addAll(getDerivedIdentities(derivedIdentity));
            }
        }
        return options;
    }
}
