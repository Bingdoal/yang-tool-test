package yangparser.schema;

import lombok.Data;

import java.util.Optional;

@Data
public class RpcDto {
    private Optional<String> description;
    private Optional<ContainerDto> input;
    private Optional<ContainerDto> output;
}
