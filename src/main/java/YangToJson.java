import com.google.common.collect.Range;
import org.opendaylight.yangtools.yang.common.YangConstants;
import org.opendaylight.yangtools.yang.model.api.*;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.type.*;
import org.opendaylight.yangtools.yang.model.parser.api.YangSyntaxErrorException;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.parser.rfc7950.repo.YangStatementStreamSource;
import org.opendaylight.yangtools.yang.parser.stmt.reactor.EffectiveSchemaContext;
import schema.*;
import schema.type.BitsType;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;

public class YangToJson {
    public final FileFilter YANG_FILE_FILTER =
            file -> file.getName().endsWith(YangConstants.RFC6020_YANG_FILE_EXTENSION) && file.isFile();
    public EffectiveSchemaContext schemaContext;
    private Module module;

    public Collection<YangStatementStreamSource> getSource(String path) throws URISyntaxException, IOException, YangSyntaxErrorException {
        File sourcesDir = new File(Main.class.getResource(path).toURI());
        File[] files = sourcesDir.listFiles(YANG_FILE_FILTER);
        Collection<YangStatementStreamSource> sources = new ArrayList<>(files.length);
        for (File file : files) {
            sources.add(YangStatementStreamSource.create(YangTextSchemaSource.forFile(file)));
        }
        return sources;
    }

    public ModuleDto convertToDto(EffectiveSchemaContext schemaContext, Module module) {
        this.schemaContext = schemaContext;
        this.module = module;

        ModuleDto moduleDto = new ModuleDto();
        moduleDto.setName(module.getName());
        moduleDto.setNameSpace(module.getNamespace().toString());
        if (module.getRevision().isPresent()) {
            moduleDto.setRevision(module.getRevision().get().toString());
        }
        moduleDto.setDataTree(Optional.of(getModuleDataTree(module.getChildNodes())));
        return moduleDto;
    }

    private DataTreeDto getModuleDataTree(Collection<? extends DataSchemaNode> childNodes) {
        DataTreeDto dataTreeDto = new DataTreeDto();
        Map<String, LeafDto> leaves = new HashMap<>();
        Map<String, ContainerDto> containers = new HashMap<>();
        for (DataSchemaNode node : childNodes) {
            if (node.getStatus() == Status.DEPRECATED) {
                continue;
            }
            putNodeInLeavesOrContainers(leaves, containers, node);
        }
        if (!leaves.isEmpty()) {
            dataTreeDto.setLeaves(Optional.of(leaves));
        }
        if (!containers.isEmpty()) {
            dataTreeDto.setContainers(Optional.of(containers));
        }
        return dataTreeDto;
    }

    private ContainerDto getContainer(DataSchemaNode childNode) {
        DataNodeContainer dataNodeContainer = (DataNodeContainer) childNode;

        ContainerDto containerDto = new ContainerDto();
        Map<String, LeafDto> leaves = new HashMap<>();
        Map<String, ContainerDto> containers = new HashMap<>();

        containerDto.setStatus(childNode.getStatus().toString());
        if (childNode.effectiveConfig().isPresent()) {
            containerDto.setConfig(childNode.effectiveConfig().get());
        }
        if (childNode instanceof ListSchemaNode) {
            ListSchemaNode listSchemaNode = (ListSchemaNode) childNode;
            containerDto.setList(true);
            containerDto.setKey(Optional.of(listSchemaNode.getKeyDefinition().get(0).getLocalName()));
        }


        for (DataSchemaNode node : dataNodeContainer.getChildNodes()) {
            if (node.getStatus() == Status.DEPRECATED) {
                continue;
            }
            putNodeInLeavesOrContainers(leaves, containers, node);
        }
        if (!leaves.isEmpty()) {
            containerDto.setLeaves(Optional.of(leaves));
        }
        if (!containers.isEmpty()) {
            containerDto.setContainers(Optional.of(containers));
        }
        return containerDto;
    }

    private void putNodeInLeavesOrContainers(Map<String, LeafDto> leaves, Map<String, ContainerDto> containers, DataSchemaNode node) {
        String nodeName = node.getQName().getLocalName();
        if (node.isAugmenting()) {
            String moduleName = schemaContext.findModules(node.getQName().getNamespace())
                    .iterator().next().getName();
            nodeName = moduleName + ":" + nodeName;
        }
        if (node instanceof DataNodeContainer) {
            containers.put(nodeName, getContainer(node));
        } else if (isLeafLikeNode(node)) {
            leaves.put(nodeName, getDataNode(node));
        }
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
        leafDto.setStatus(childNode.getStatus().toString());
        if (childNode.effectiveConfig().isPresent()) {
            leafDto.setConfig(childNode.effectiveConfig().get());
        }
        if (childNode instanceof LeafSchemaNode) {
            LeafSchemaNode leaf = (LeafSchemaNode) childNode;
            setTypeInfo(leaf.getType(), leafDto);
        } else if (childNode instanceof LeafListSchemaNode) {
            LeafListSchemaNode leafList = (LeafListSchemaNode) childNode;
            leafDto.setList(true);
            setTypeInfo(leafList.getType(), leafDto);
        } else if (childNode instanceof AnydataSchemaNode) {
            AnydataSchemaNode anydata = (AnydataSchemaNode) childNode;
            leafDto.setType("anydata");
        } else if (childNode instanceof AnyxmlSchemaNode) {
            AnyxmlSchemaNode anyxml = (AnyxmlSchemaNode) childNode;
            leafDto.setType("anyxml");
        } else if (childNode instanceof ChoiceSchemaNode) {
            setChoiceInfo((ChoiceSchemaNode) childNode, leafDto);
        } else {
            System.out.println("Unknow Node: " + childNode.getQName().getLocalName());
        }
        return leafDto;
    }

    private void setChoiceInfo(ChoiceSchemaNode choiceNode, LeafDto leafDto) {
        leafDto.setType("choice");
        if (choiceNode.getDefaultCase().isPresent()) {
            leafDto.setDefaultCase(Optional.of(choiceNode.getDefaultCase().get().getQName().getLocalName()));
        }

        Map<String, CaseDto> cases = new HashMap<>();
        for (CaseSchemaNode caseNode : choiceNode.getCases()) {
            CaseDto caseDto = new CaseDto();
            Map<String, LeafDto> leaves = new HashMap<>();
            Map<String, ContainerDto> containers = new HashMap<>();

            for (DataSchemaNode node : caseNode.getChildNodes()) {
                if (node.getStatus() == Status.DEPRECATED) {
                    continue;
                }
                putNodeInLeavesOrContainers(leaves, containers, node);
            }

            if (!leaves.isEmpty()) {
                caseDto.setLeaves(Optional.of(leaves));
            }
            if (!containers.isEmpty()) {
                caseDto.setContainers(Optional.of(containers));
            }
            cases.put(caseNode.getQName().getLocalName(), caseDto);
        }

        leafDto.setCases(Optional.of(cases));
    }

    private void setTypeInfo(TypeDefinition<? extends TypeDefinition<?>> nodeType, LeafDto leafDto) {
        TypeDefinition<? extends TypeDefinition<?>> typeDefinition = nodeType;
        if (nodeType.getBaseType() != null) {
            leafDto.setType(nodeType.getBaseType().getQName().getLocalName());
        } else {
            leafDto.setType(nodeType.getQName().getLocalName());
        }
        if (nodeType.getDefaultValue().isPresent()) {
            leafDto.setDefaultValue((Optional<String>) (nodeType.getDefaultValue()));
        }

        if (typeDefinition instanceof RangeRestrictedTypeDefinition) {
            RangeRestrictedTypeDefinition rangeRestrictedType = (RangeRestrictedTypeDefinition) typeDefinition;
            Optional<RangeConstraint> rangeConstraint = rangeRestrictedType.getRangeConstraint();
            if (rangeConstraint.isPresent()) {
                Range range = rangeConstraint.get().getAllowedRanges().span();
                leafDto.setRange(Optional.of(range.lowerEndpoint() + "~" + range.upperEndpoint()));
            }
        }

        if (typeDefinition instanceof EnumTypeDefinition) {
            EnumTypeDefinition enumType = (EnumTypeDefinition) typeDefinition;
            List<String> options = new ArrayList<>();
            for (EnumTypeDefinition.EnumPair value : enumType.getValues()) {
                options.add(value.getValue() + ":" + value.getName());
            }
            leafDto.setOptions(Optional.of(options));

        } else if (typeDefinition instanceof IdentityrefTypeDefinition) {
            IdentityrefTypeDefinition identityrefType = (IdentityrefTypeDefinition) typeDefinition;
            leafDto.setOptions(Optional.of(getIdentityOptions(identityrefType.getIdentities())));

        } else if (typeDefinition instanceof LeafrefTypeDefinition) {
            LeafrefTypeDefinition leafrefType = (LeafrefTypeDefinition) typeDefinition;
            leafDto.setPath(Optional.of(leafrefType.getPathStatement().getOriginalString()));

        } else if (typeDefinition instanceof DecimalTypeDefinition) {
            DecimalTypeDefinition decimalType = (DecimalTypeDefinition) typeDefinition;
            leafDto.setFractionDigits(Optional.of(decimalType.getFractionDigits()));

        } else if (typeDefinition instanceof StringTypeDefinition) {
            StringTypeDefinition stringType = (StringTypeDefinition) typeDefinition;
            List<String> patterns = new ArrayList<>();
            for (PatternConstraint patternConstraint : stringType.getPatternConstraints()) {
                patterns.add(patternConstraint.getRegularExpressionString());
            }
            if (patterns.size() > 0) {
                leafDto.setPattern(Optional.of(patterns));
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
            leafDto.setBits(Optional.of(bitsTypes));

        } else if (!(typeDefinition instanceof RangeRestrictedTypeDefinition
                || typeDefinition instanceof BooleanTypeDefinition)) {
            System.out.println("Unknow type: " + typeDefinition.getQName().getLocalName());
        }
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
