import com.google.common.collect.Range;
import org.opendaylight.yangtools.yang.model.api.*;
import org.opendaylight.yangtools.yang.model.api.type.*;
import schema.type.BitsType;

import java.util.*;

public class ParserDebugger {

    public static void getContainer(DataNodeContainer dataNodeContainer, int level) {
        Main.printIndent(level);
        System.out.println(dataNodeContainer.toString());
        for (DataSchemaNode node : dataNodeContainer.getChildNodes()) {
            if (node instanceof DataNodeContainer) {
                getContainer((DataNodeContainer) node, level + 1);
            } else {
                getDataNode(node, level + 1);
            }
        }
    }

    public static void getDataNode(DataSchemaNode childNode, int level) {
        Main.printIndent(level);
        System.out.println(childNode.getQName().getLocalName());

        if (childNode instanceof LeafSchemaNode) {
            LeafSchemaNode leaf = (LeafSchemaNode) childNode;
            setTypeInfo(leaf.getType(), level + 1);
        } else if (childNode instanceof LeafListSchemaNode) {
            LeafListSchemaNode leafList = (LeafListSchemaNode) childNode;
            setTypeInfo(leafList.getType(), level + 1);
        } else if (childNode instanceof AnydataSchemaNode) {
            Main.printIndent(level + 2);
            System.out.println("type: anydata");
        } else if (childNode instanceof AnyxmlSchemaNode) {
            Main.printIndent(level + 2);
            System.out.println("type: anyxml");
        } else if (childNode instanceof ChoiceSchemaNode) {
            Main.printIndent(level + 2);
            System.out.println("type: choice");
        } else {
            Main.printIndent(level + 2);
            System.out.println("type: " + childNode.toString());
        }
    }

    public static void setTypeInfo(TypeDefinition<? extends TypeDefinition<?>> nodeType, int level) {
        TypeDefinition<? extends TypeDefinition<?>> typeDefinition = nodeType;
        Main.printIndent(level);
        String type = typeDefinition.getQName().getLocalName();
        if (typeDefinition.getBaseType() != null) {
            type = typeDefinition.getBaseType().getQName().getLocalName();
        }
        System.out.println("Type: " + type);

        if (typeDefinition instanceof RangeRestrictedTypeDefinition) {
            RangeRestrictedTypeDefinition rangeRestrictedType = (RangeRestrictedTypeDefinition) typeDefinition;
            Optional<RangeConstraint> rangeConstraint = rangeRestrictedType.getRangeConstraint();
            if (rangeConstraint.isPresent()) {
                Range range = rangeConstraint.get().getAllowedRanges().span();
                Main.printIndent(level + 1);
                System.out.println("Range: " + range.lowerEndpoint() + "~" + range.upperEndpoint());
            }
        }

        if (typeDefinition instanceof EnumTypeDefinition) {
            EnumTypeDefinition enumType = (EnumTypeDefinition) typeDefinition;
            List<String> options = new ArrayList<>();
            for (EnumTypeDefinition.EnumPair value : enumType.getValues()) {
                options.add(value.getValue() + ":" + value.getName());
            }
            Main.printIndent(level + 1);
            System.out.println("Options: " + Arrays.toString(options.toArray()));

        } else if (typeDefinition instanceof IdentityrefTypeDefinition) {
            IdentityrefTypeDefinition identityrefType = (IdentityrefTypeDefinition) typeDefinition;
            List<String> options = getDerivedIdentity(identityrefType.getIdentities());
            Main.printIndent(level + 1);
            System.out.println("Options: " + Arrays.toString(options.toArray()));

        } else if (typeDefinition instanceof LeafrefTypeDefinition) {
            LeafrefTypeDefinition leafrefType = (LeafrefTypeDefinition) typeDefinition;
            Main.printIndent(level + 1);
            System.out.println("Path: " + leafrefType.getPathStatement().getOriginalString());

        } else if (typeDefinition instanceof DecimalTypeDefinition) {
            DecimalTypeDefinition decimalType = (DecimalTypeDefinition) typeDefinition;
            Main.printIndent(level + 1);
            System.out.println("FractionDigits: " + decimalType.getFractionDigits());

        } else if (typeDefinition instanceof StringTypeDefinition) {
            StringTypeDefinition stringType = (StringTypeDefinition) typeDefinition;
            List<String> patterns = new ArrayList<>();
            for (PatternConstraint patternConstraint : stringType.getPatternConstraints()) {
                patterns.add(patternConstraint.getRegularExpressionString());
            }
            if (patterns.size() > 0) {
                Main.printIndent(level + 1);
                System.out.println("Patterns: " + Arrays.toString(patterns.toArray()));
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
            Main.printIndent(level + 1);
            System.out.println("Bits: " + Arrays.toString(bitsTypes.toArray()));

        } else if (!(typeDefinition instanceof RangeRestrictedTypeDefinition)) {
            Main.printIndent(level + 1);
            System.out.println("Unknow type: " + typeDefinition.getQName().getLocalName());
        }
    }

    public static List<String> getDerivedIdentity(Collection<? extends IdentitySchemaNode> identities) {
        List<String> options = new ArrayList<>();
        for (IdentitySchemaNode identity : identities) {
            options.add(identity.getQName().getLocalName());
            options.addAll(getBaseIdentity(identity));
        }
        return options;
    }

    public static List<String> getBaseIdentity(IdentitySchemaNode identity) {
        List<String> options = new ArrayList<>();
        for (IdentitySchemaNode derivedIdentity : Main.schemaContext.getDerivedIdentities(identity)) {
            options.add(derivedIdentity.getQName().getLocalName());
            if (Main.schemaContext.getDerivedIdentities(derivedIdentity).size() > 0) {
                options.addAll(getBaseIdentity(derivedIdentity));
            }
        }
        return options;
    }

    public static void getRpc(RpcDefinition rpc, int level) {
        Main.printIndent(level);
        System.out.println("Input:");
        for (DataSchemaNode childNode : rpc.getInput().getChildNodes()) {
            if (childNode instanceof DataNodeContainer) {
                ParserDebugger.getContainer((DataNodeContainer) childNode, level + 1);
            } else {
                ParserDebugger.getDataNode(childNode, level + 1);
            }
        }
        Main.printIndent(level);
        System.out.println("Output:");
        for (DataSchemaNode childNode : rpc.getOutput().getChildNodes()) {
            if (childNode instanceof DataNodeContainer) {
                ParserDebugger.getContainer((DataNodeContainer) childNode, level + 1);
            } else {
                ParserDebugger.getDataNode(childNode, level + 1);
            }
        }
    }
}
