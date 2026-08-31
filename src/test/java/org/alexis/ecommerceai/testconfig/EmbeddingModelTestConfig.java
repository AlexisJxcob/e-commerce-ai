package org.alexis.ecommerceai.testconfig;

import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Sustituye el EmbeddingModel real (Hugging Face) por un stub determinista que
 * devuelve un vector de 384 dimensiones (coincide con la columna
 * vector(384) de la entidad Producto). Los tests no dependen de servicios externos.
 */
@TestConfiguration
public class EmbeddingModelTestConfig {

    public static final int DIMENSION = 384;

    @Bean
    @Primary
    public EmbeddingModel testEmbeddingModel() {
        EmbeddingModel model = Mockito.mock(EmbeddingModel.class);
        float[] vector = new float[DIMENSION];
        vector[0] = 1.0f;
        Mockito.when(model.embed(ArgumentMatchers.anyString())).thenReturn(vector);
        return model;
    }
}
