package yangparser;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.google.common.collect.Range;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.*;
import org.opendaylight.yangtools.yang.model.api.meta.DeclaredStatement;
import org.opendaylight.yangtools.yang.model.api.meta.EffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.IfFeatureAwareDeclaredStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.IfFeatureStatement;
import org.opendaylight.yangtools.yang.model.api.type.*;
import org.opendaylight.yangtools.yang.parser.stmt.reactor.EffectiveSchemaContext;
import org.opendaylight.yangtools.yang.xpath.api.YangXPathExpression;
import yangparser.schema.*;
import yangparser.schema.type.BitsType;
import yangparser.schema.type.EnumType;
import yangparser.schema.type.IdentityType;
import yangparser.schema.type.TypeProperty;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;

public class YangToJson {
    public EffectiveSchemaContext schemaContext;
    private Module module;
    private ObjectMapper mapper;

    public YangToJson() {
        mapper = new ObjectMapper();
        mapper.registerModule(new Jdk8Module());
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
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

        DataTreeDto dataTreeDto = getDataTree(module.getChildNodes());
        if (!dataTreeDto.isEmpty()) {
            moduleDto.setDataTree(dataTreeDto);
        }

        Map<String, RpcDto> rpcTree = getRpcTree(module.getRpcs());
        if (!rpcTree.isEmpty()) {
            moduleDto.setRpc(rpcTree);
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
            ContainerDto containerDto = getContainer(notification);
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

            ContainerDto input = getContainer(rpc.getInput());
            ContainerDto output = getContainer(rpc.getOutput());
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


    private DataTreeDto getDataTree(Collection<? extends DataSchemaNode> childNodes) {
        DataTreeDto dataTreeDto = new DataTreeDto();
        Map<String, LeafDto> leaves = new HashMap<>();
        Map<String, ContainerDto> containers = new HashMap<>();
        Map<String, LeafDto> leafList = new HashMap<>();
        Map<String, ContainerDto> containerList = new HashMap<>();
        for (DataSchemaNode node : childNodes) {
            String nodeName = node.getQName().getLocalName();
            if (node.isAugmenting()) {
                String moduleName = schemaContext.findModules(node.getQName().getNamespace())
                        .iterator().next().getName();
                nodeName = moduleName + ":" + nodeName;
            }

            if (node instanceof DataNodeContainer) {
                ContainerDto tmpContainer = getContainer((DataNodeContainer) node);
                if (tmpContainer.isArray()) {
                    containerList.put(nodeName, tmpContainer);
                } else {
                    containers.put(nodeName, tmpContainer);
                }
            } else if (isLeafLikeNode(node)) {
                LeafDto tmpLeaf = getDataNode(node);
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

    private ContainerDto getContainer(DataNodeContainer dataNodeContainer) {

        ContainerDto containerDto = new ContainerDto();
        if (dataNodeContainer instanceof DataSchemaNode) {
            DataSchemaNode childNode = (DataSchemaNode) dataNodeContainer;
            containerDto.setStatus(childNode.getStatus());
            containerDto.setConfig(childNode.isConfiguration());
            List<String> mustList = getMustConditions(childNode);
            if (!mustList.isEmpty()) {
                containerDto.setMust(Optional.of(mustList));
            }

            if (childNode.getWhenCondition().isPresent()) {
                String when = childNode.getWhenCondition().get().toString().replaceAll("[\r\n\\s]", "");
                containerDto.setWhen(Optional.of(when));
            }

            List<String> ifFeatures = getIfFeatures(childNode);
            if (!ifFeatures.isEmpty()) {
                containerDto.setIfFeature(Optional.of(ifFeatures));
            }

            if (childNode.getDescription().isPresent()) {
                containerDto.setDescription(childNode.getDescription());
            }

            if (childNode instanceof ListSchemaNode) {
                ListSchemaNode listSchemaNode = (ListSchemaNode) childNode;
                containerDto.setArray(true);
                if (listSchemaNode.getKeyDefinition().size() > 0) {
                    containerDto.setKey(Optional.of(listSchemaNode.getKeyDefinition().get(0).getLocalName()));
                }
            }
            containerDto.setXpath(MySchemaContextUtils.getXpathFromSchemaNode(schemaContext, childNode));
        }

        DataTreeDto dataTreeDto = getDataTree(dataNodeContainer.getChildNodes());

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

    private LeafDto getDataNode(DataSchemaNode childNode) {
        LeafDto leafDto = new LeafDto();
        leafDto.setStatus(childNode.getStatus());
        leafDto.setConfig(childNode.isConfiguration());
        leafDto.setXpath(MySchemaContextUtils.getXpathFromSchemaNode(schemaContext, childNode));

        if (childNode.getDescription().isPresent()) {
            leafDto.setDescription(childNode.getDescription());
        }

        if (childNode instanceof MandatoryAware) {
            MandatoryAware mandatoryAware = (MandatoryAware) childNode;
            leafDto.setMandatory(mandatoryAware.isMandatory());
        }

        if (childNode instanceof TypedDataSchemaNode) {
            TypedDataSchemaNode typedData = (TypedDataSchemaNode) childNode;
            TypeProperty type = getTypeInfo(childNode, typedData.getType());
            leafDto.setType(type.getName());
            leafDto.setTypeProperty(type);
            if (type.getLeafref() != null && type.getLeafref().isPresent()) {
                leafDto.setMandatory(true);
            }
        }

        List<String> mustList = getMustConditions(childNode);
        if (!mustList.isEmpty()) {
            leafDto.setMust(Optional.of(mustList));
        }

        if (childNode.getWhenCondition().isPresent()) {
            String when = childNode.getWhenCondition().get().toString().replaceAll("[\r\n\\s]", "");
            leafDto.setWhen(Optional.of(when));
        }

        List<String> ifFeatures = getIfFeatures(childNode);
        if (!ifFeatures.isEmpty()) {
            leafDto.setIfFeature(Optional.of(ifFeatures));
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

    private List<String> getIfFeatures(DataSchemaNode childNode) {
        List<String> ifFeatures = new ArrayList<>();
        if (childNode instanceof EffectiveStatement) {
            EffectiveStatement effectiveStatement = (EffectiveStatement) childNode;
            DeclaredStatement declaredStatement = effectiveStatement.getDeclared();
            if (declaredStatement instanceof IfFeatureAwareDeclaredStatement) {
                IfFeatureAwareDeclaredStatement ifFeatureAware =
                        (IfFeatureAwareDeclaredStatement) declaredStatement;
                if (!ifFeatureAware.getIfFeatures().isEmpty()) {
                    for (Object ifFeature : ifFeatureAware.getIfFeatures()) {
                        IfFeatureStatement ifFeatureStatement = (IfFeatureStatement) ifFeature;
                        for (QName referencedFeature : ifFeatureStatement.getIfFeaturePredicate().getReferencedFeatures()) {
                            ifFeatures.add(referencedFeature.getLocalName());
                        }
                    }
                }
            }
        }
        return ifFeatures;
    }

    private List<String> getMustConditions(DataSchemaNode childNode) {
        List<String> mustList = new ArrayList<>();
        if (childNode instanceof MustConstraintAware) {
            MustConstraintAware mustConstraint = (MustConstraintAware) childNode;
            if (!mustConstraint.getMustConstraints().isEmpty()) {
                mustList.addAll(mustConstraint.getMustConstraints().stream()
                        .map((must) -> must.getXpath().toString())
                        .collect(Collectors.toList()));
            }
        }
        return mustList;
    }

    private TypeProperty getChoiceTypeInfo(ChoiceSchemaNode choiceNode) {
        TypeProperty typeProperty = new TypeProperty();
        typeProperty.setName("choice");
        if (choiceNode.getDefaultCase().isPresent()) {
            typeProperty.setDefaultCase(Optional.of(choiceNode.getDefaultCase().get().getQName().getLocalName()));
        }

        Map<String, DataTreeDto> cases = new HashMap<>();
        for (CaseSchemaNode caseNode : choiceNode.getCases()) {
            cases.put(caseNode.getQName().getLocalName(), getDataTree(caseNode.getChildNodes()));
        }

        typeProperty.setCases(Optional.of(cases));
        return typeProperty;
    }

    private TypeProperty getTypeInfo(DataSchemaNode dataSchemaNode, TypeDefinition<? extends TypeDefinition<?>> nodeType) {
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
            typeProperty.setMin(Optional.of(baseRange.lowerEndpoint().toString()));
            typeProperty.setMax(Optional.of(baseRange.upperEndpoint().toString()));
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
                typeProperty.setMin(Optional.of(baseLength.lowerEndpoint().toString()));
                typeProperty.setMax(Optional.of(baseLength.upperEndpoint().toString()));
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
            typeProperty.setIdentities(Optional.of(getDerivedIdentities(baseIdentity)));
            typeProperty.setBase(Optional.of(baseIdentity.getQName().getLocalName()));

        } else if (nodeType instanceof LeafrefTypeDefinition) {
            LeafrefTypeDefinition leafrefType = (LeafrefTypeDefinition) nodeType;
            TypeDefinition refType = MySchemaContextUtils.getBaseTypeForLeafRef(leafrefType, schemaContext, dataSchemaNode);
            typeProperty = getTypeInfo(dataSchemaNode, refType);

            typeProperty.setRequireInstance(Optional.of(leafrefType.requireInstance()));
            DataSchemaNode leafrefNode = MySchemaContextUtils.getSchemaNodeForLeafRef(leafrefType, schemaContext, dataSchemaNode);
            String leafrefXpath = MySchemaContextUtils.getXpathFromSchemaNode(schemaContext, leafrefNode);
            typeProperty.setLeafref(Optional.of(leafrefXpath));
            typeProperty.setLeafrefOrigin(Optional.of(leafrefType.getPathStatement().getOriginalString()));
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
                bitsTypes.add(bitT);
            }
            typeProperty.setBits(Optional.of(bitsTypes));

        } else if (nodeType instanceof UnionTypeDefinition) {
            UnionTypeDefinition unionType = (UnionTypeDefinition) nodeType;
            List<TypeProperty> types = new ArrayList<>();
            for (TypeDefinition<?> type : unionType.getTypes()) {
                TypeProperty tmp = getTypeInfo(dataSchemaNode, type);
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
