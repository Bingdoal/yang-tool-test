package schema;

import lombok.Data;
import schema.type.BitsType;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Data
public class LeafDto {
    private String type;
    private boolean config = true;
    private boolean isList = false;
    private Optional<String> defaultValue;
    private Optional<String> description;

    private Optional<List<BitsType>> bits; // type bits
    private Optional<String> path; // type leafref
    private Optional<String> range; // type int, decimal
    private Optional<Integer> fractionDigits; // type decimal
    private Optional<List<String>> pattern; // type string
    private Optional<List<String>> options; // type enumeration, identityref
    private Optional<String> base; // type identityref

    // type choice
    private Optional<Map<String, CaseDto>> cases;
    private Optional<String> defaultCase;
}
