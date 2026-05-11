package com.aibridge.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.aibridge.model.Feature;
import com.aibridge.repository.FeatureRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminFeatureResourceTest {

    @Mock
    FeatureRepository featureRepository;

    @InjectMocks
    AdminFeatureResource adminFeatureResource;

    @Test
    void listDistinctFeatures_whenEmpty_returnsEmptyList() {
        when(featureRepository.listAll()).thenReturn(List.of());

        List<String> out = adminFeatureResource.listDistinctFeatures();

        assertTrue(out.isEmpty());
    }

    @Test
    void listDistinctFeatures_deduplicatesSortsAndFiltersBlanks() {
        Feature a = new Feature();
        a.setFeature("beta");
        Feature b = new Feature();
        b.setFeature("alpha");
        Feature c = new Feature();
        c.setFeature("beta");
        Feature d = new Feature();
        d.setFeature("   ");
        Feature e = new Feature();
        e.setFeature(null);
        Feature f = new Feature();
        f.setFeature("gamma");

        when(featureRepository.listAll()).thenReturn(List.of(a, b, c, d, e, f));

        List<String> out = adminFeatureResource.listDistinctFeatures();

        assertEquals(List.of("alpha", "beta", "gamma"), out);
    }

    @Test
    void listDistinctFeatures_whenAllNullOrBlank_returnsEmpty() {
        Feature x = new Feature();
        x.setFeature(null);
        Feature y = new Feature();
        y.setFeature("");
        Feature z = new Feature();
        z.setFeature("\t");

        when(featureRepository.listAll()).thenReturn(List.of(x, y, z));

        assertTrue(adminFeatureResource.listDistinctFeatures().isEmpty());
    }
}
