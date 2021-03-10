package schema;

import java.util.Map;
import java.util.Optional;

public class CaseDto {
    private Optional<Map<String, LeafDto>> leaves;
    private Optional<Map<String, ContainerDto>> containers;
}
