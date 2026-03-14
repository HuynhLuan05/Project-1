package com.yas.recommendation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yas.recommendation.configuration.RecommendationConfig;
import com.yas.recommendation.viewmodel.ProductDetailVm;
import java.net.URI;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private RestClient restClient;

    @Mock
    private RecommendationConfig config;

    @Mock
    private RestClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private RestClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    @InjectMocks
    private ProductService productService;

    @Test
    void shouldFetchProductDetailUsingConfiguredStorefrontUrl() {
        long productId = 42L;
        ProductDetailVm expected = new ProductDetailVm(
                productId,
                "Test Product",
                "Short description",
                "Description",
                "Specification",
                "SKU-42",
                "GTIN-42",
                "test-product",
                true,
                true,
                false,
                true,
                true,
                99.99,
                7L,
                Collections.emptyList(),
                "Meta title",
                "Meta keyword",
                "Meta description",
                3L,
                "Brand",
                Collections.emptyList(),
                Collections.emptyList(),
                null,
                Collections.emptyList()
        );

        when(config.getApiUrl()).thenReturn("http://product-service/api");
        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(URI.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toEntity(any(ParameterizedTypeReference.class))).thenReturn(ResponseEntity.ok(expected));

        ProductDetailVm actual = productService.getProductDetail(productId);

        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        verify(requestHeadersUriSpec).uri(uriCaptor.capture());
        assertEquals("http://product-service/api/storefront/products/detail/42", uriCaptor.getValue().toString());
        assertSame(expected, actual);
    }
}
