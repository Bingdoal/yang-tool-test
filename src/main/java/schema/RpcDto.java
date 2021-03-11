package schema;

import lombok.Data;

import java.util.Optional;

@Data
public class RpcDto {
    private Optional<ContainerDto> input;
    private Optional<ContainerDto> output;
}
