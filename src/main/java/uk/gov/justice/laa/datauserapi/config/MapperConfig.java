package uk.gov.justice.laa.datauserapi.config;

import org.modelmapper.Converter;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;

@Configuration
public class MapperConfig {

    @Bean
    public ModelMapper modelMapper() {
        ModelMapper modelMapper = new ModelMapper();
        modelMapper.getConfiguration().setFieldMatchingEnabled(true)
                .setSkipNullEnabled(true)
                .setAmbiguityIgnored(true)
                .setMatchingStrategy(MatchingStrategies.STRICT);
        addEntraUserToEntraUserDtoTypeMap(modelMapper);
        return modelMapper;
    }

    public void addEntraUserToEntraUserDtoTypeMap(ModelMapper modelMapper) {
        // Define custom converter
        Converter<EntraUser, String> fullNameConverter = context -> {
            EntraUser source = context.getSource();
            if (source.getFirstName() == null) {
                return null;
            }
            String lastName = source.getLastName() != null ? source.getLastName() : "";
            return String.format("%s %s", source.getFirstName(), lastName).trim();
        };

        // Map Source to Destination
        modelMapper.typeMap(EntraUser.class, EntraUserDto.class).addMappings(mapper -> mapper.using(fullNameConverter).map(src -> src, EntraUserDto::setFullName));
    }


}

