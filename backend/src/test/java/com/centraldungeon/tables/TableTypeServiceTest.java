package com.centraldungeon.tables;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.centraldungeon.tables.dto.TableTypeResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * What the wizard's selector reads. The one rule worth pinning is #225: a type the application
 * shipped travels with its {@code code} so the reader sees it in their own language, and a type a
 * person named travels without one so its label is read exactly as typed.
 */
@ExtendWith(MockitoExtension.class)
class TableTypeServiceTest {

    @Mock
    private TableTypeRepository tableTypeRepository;

    @InjectMocks
    private TableTypeService tableTypeService;

    @Test
    @DisplayName("un tipo que trajo la aplicación viaja con su código, para que el frontend lo traduzca")
    void seededTypeCarriesItsCode() {
        when(tableTypeRepository.findAll(any(Pageable.class))).thenReturn(page(tableType("tt-1", "PUBLIC", "Public")));

        TableTypeResponse type = tableTypeService.list(PageRequest.of(0, 20)).content().getFirst();

        assertThat(type.code()).isEqualTo("PUBLIC");
        // The name still travels: it is the fallback for a reader whose language has no translation
        // for this code yet, and it is what the admin screens show.
        assertThat(type.name()).isEqualTo("Public");
    }

    @Test
    @DisplayName("un tipo que creó una persona viaja sin código, y su nombre se lee tal cual")
    void adminCreatedTypeHasNoCode() {
        when(tableTypeRepository.findAll(any(Pageable.class))).thenReturn(page(tableType("tt-9", null, "Mesa de campaña larga")));

        TableTypeResponse type = tableTypeService.list(PageRequest.of(0, 20)).content().getFirst();

        assertThat(type.code()).isNull();
        assertThat(type.name()).isEqualTo("Mesa de campaña larga");
    }

    private static PageImpl<TableType> page(TableType type) {
        return new PageImpl<>(List.of(type));
    }

    /** Nothing in the application creates a table type, so the fields are set the only way left. */
    private static TableType tableType(String id, String code, String name) {
        TableType type = new TableType();
        ReflectionTestUtils.setField(type, "id", id);
        ReflectionTestUtils.setField(type, "code", code);
        ReflectionTestUtils.setField(type, "name", name);
        return type;
    }
}
