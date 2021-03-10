package schema;

import lombok.Data;

import java.util.Map;
import java.util.Optional;

@Data
public class CaseDto {
    private Optional<Map<String, LeafDto>> leaves;
    private Optional<Map<String, ContainerDto>> containers;
}
