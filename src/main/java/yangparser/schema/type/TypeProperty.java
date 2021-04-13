package yangparser.schema.type;

import lombok.Data;
import yangparser.schema.DataTreeDto;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Data
public class TypeProperty {

    public TypeProperty() {
    }

    public TypeProperty(String name) {
        this.setName(name);
    }

    private String name;
    private Optional<String> description;
    private Optional<String> units;

    private Optional<String> defaultValue;

    private Optional<List<BitsType>> bits; // type bits
    private Optional<String> leafref; // type leafref
    private Optional<Boolean> requireInstance; // type leafref, instance-identity

    private Optional<String> min; // type int, decimal
    private Optional<String> max; // type int, decimal
    private Optional<Integer> fractionDigits; // type decimal

    private Optional<List<String>> pattern; // type string
    private Optional<String> length; // type string, binary
    private Optional<List<EnumType>> options; // type enumeration

    private Optional<List<IdentityType>> identities; // type identityref
    private Optional<String> base; // type identityref
    private Optional<List<TypeProperty>> unionTypes; // type union

    // type choice
    private Optional<Map<String, DataTreeDto>> cases;
    private Optional<String> defaultCase;

}
