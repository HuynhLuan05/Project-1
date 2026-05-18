package com.yas.search.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import com.yas.search.constant.enums.SortType;
import com.yas.search.model.Product;
import com.yas.search.model.ProductCriteriaDto;
import com.yas.search.viewmodel.ProductListGetVm;
import com.yas.search.viewmodel.ProductNameGetVm;
import com.yas.search.viewmodel.ProductNameListVm;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.AggregationsContainer;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.SearchShardStatistics;
import org.springframework.data.elasticsearch.core.TotalHitsRelation;
import org.springframework.data.elasticsearch.core.suggest.response.Suggest;

class ProductServiceTest {

    private ElasticsearchOperations elasticsearchOperations;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        elasticsearchOperations = mock(ElasticsearchOperations.class);
        productService = new ProductService(elasticsearchOperations);
    }

    @Test
    void testFindProductAdvance_whenSortTypeIsPriceAsc_ReturnProductListGetVm() {
        SearchHits<Product> searchHits = getSearchHits(buildProduct("Test Product"));

        ArgumentCaptor<NativeQuery> captor = ArgumentCaptor.forClass(NativeQuery.class);

        when(elasticsearchOperations.search(any(NativeQuery.class), eq(Product.class))).thenReturn(searchHits);

        ProductCriteriaDto criteriaDto = new ProductCriteriaDto(
            "test", 0, 10, "testBrand", "testCategory",
            "testAttribute", 10.0, 100.0, SortType.PRICE_ASC);
        ProductListGetVm result = productService.findProductAdvance(criteriaDto);

        verify(elasticsearchOperations, times(1))
            .search(captor.capture(), eq(Product.class));
        assertEquals("price: ASC", Objects.requireNonNull(captor.getValue().getSort()).toString());

        assertNotNull(result);
        assertEquals(1, result.products().size());
        assertEquals(0, result.pageNo());
        assertEquals(10, result.pageSize());
        assertEquals(1, result.totalElements());
        assertTrue(result.isLast());
    }

    @Test
    void testFindProductAdvance_whenSortTypeIsPriceDesc_ReturnProductListGetVm() {
        SearchHits<Product> searchHits = getSearchHits(buildProduct("Test Product"));

        ArgumentCaptor<NativeQuery> captor = ArgumentCaptor.forClass(NativeQuery.class);

        when(elasticsearchOperations.search(any(NativeQuery.class), eq(Product.class))).thenReturn(searchHits);

        ProductCriteriaDto criteriaDto = new ProductCriteriaDto("test", 0, 10, "testBrand", "testCategory",
            "testAttribute", 10.0, 100.0, SortType.PRICE_DESC);
        productService.findProductAdvance(criteriaDto);

        verify(elasticsearchOperations, times(1))
            .search(captor.capture(), eq(Product.class));

        assertEquals("price: DESC", Objects.requireNonNull(captor.getValue().getSort()).toString());
    }

    @Test
    void testFindProductAdvance_whenSortTypeIsDefault_ReturnProductListGetVm() {

        SearchHits<Product> searchHits = getSearchHits(buildProduct("Test Product"));

        ArgumentCaptor<NativeQuery> captor = ArgumentCaptor.forClass(NativeQuery.class);

        when(elasticsearchOperations.search(any(NativeQuery.class), eq(Product.class))).thenReturn(searchHits);

        ProductCriteriaDto criteriaDto = new ProductCriteriaDto(
            "test", 0, 10, "testBrand", "testCategory",
            "testAttribute", 10.0, 100.0, SortType.DEFAULT);
        productService.findProductAdvance(criteriaDto);

        verify(elasticsearchOperations, times(1))
            .search(captor.capture(), eq(Product.class));

        assertEquals("createdOn: DESC", Objects.requireNonNull(captor.getValue().getSort()).toString());
    }

    @Test
    void testAutoCompleteProductName_whenExistsProducts_returnProductNameListVm() {

        SearchHits<Product> searchHits = getSearchHits(
            buildProduct("Test Product"),
            buildProduct("Test Product 2")
        );

        when(elasticsearchOperations.search(any(NativeQuery.class), eq(Product.class)))
            .thenReturn(searchHits);

        ProductNameListVm result = productService.autoCompleteProductName("Product");

        assertNotNull(result);
        assertEquals(2, result.productNames().size());
        ProductNameGetVm productNameGetVm = result.productNames().getFirst();
        assertEquals("Test Product", productNameGetVm.name());

        verify(elasticsearchOperations).search(any(NativeQuery.class), eq(Product.class));
    }

    @Test
    @DisplayName("findProductAdvance should skip optional term and range filters when criteria are blank")
    void testFindProductAdvance_whenOptionalFiltersBlank_shouldOnlyKeepPublishedFilter() {
        SearchHits<Product> searchHits = getSearchHits(buildProduct("Test Product"));
        ArgumentCaptor<NativeQuery> captor = ArgumentCaptor.forClass(NativeQuery.class);

        when(elasticsearchOperations.search(any(NativeQuery.class), eq(Product.class))).thenReturn(searchHits);

        ProductCriteriaDto criteriaDto = new ProductCriteriaDto(
            "test", 0, 10, " ", null, "", null, null, SortType.DEFAULT
        );

        productService.findProductAdvance(criteriaDto);

        verify(elasticsearchOperations).search(captor.capture(), eq(Product.class));
        Query filterQuery = Objects.requireNonNull(captor.getValue().getFilter());

        assertNotNull(filterQuery.bool());
        assertEquals(1, filterQuery.bool().must().size());

        Query publishedQuery = filterQuery.bool().must().getFirst();
        assertNotNull(publishedQuery.term());
        assertEquals("isPublished", publishedQuery.term().field());
        assertTrue(publishedQuery.term().value().booleanValue());
    }

    @Test
    @DisplayName("findProductAdvance should build term filters for comma separated values and price range")
    void testFindProductAdvance_whenMultipleFilterValues_shouldBuildTermsAndRangeQuery() {
        SearchHits<Product> searchHits = getSearchHits(buildProduct("Test Product"));
        ArgumentCaptor<NativeQuery> captor = ArgumentCaptor.forClass(NativeQuery.class);

        when(elasticsearchOperations.search(any(NativeQuery.class), eq(Product.class))).thenReturn(searchHits);

        ProductCriteriaDto criteriaDto = new ProductCriteriaDto(
            "test", 0, 10, "nike,adidas", "shoes,sport", "red,large", 10.0, 100.0, SortType.PRICE_ASC
        );

        productService.findProductAdvance(criteriaDto);

        verify(elasticsearchOperations).search(captor.capture(), eq(Product.class));
        Query filterQuery = Objects.requireNonNull(captor.getValue().getFilter());

        assertNotNull(filterQuery.bool());
        assertEquals(5, filterQuery.bool().must().size());

        assertTermValues(filterQuery.bool().must().get(0), "brand", "nike", "adidas");
        assertTermValues(filterQuery.bool().must().get(1), "categories", "shoes", "sport");
        assertTermValues(filterQuery.bool().must().get(2), "attributes", "red", "large");

        Query rangeQuery = filterQuery.bool().must().get(3);
        assertNotNull(rangeQuery.range());
        assertEquals("price", rangeQuery.range().number().field());
        assertEquals(10.0, rangeQuery.range().number().gte().doubleValue());
        assertEquals(100.0, rangeQuery.range().number().lte().doubleValue());

        Query publishedQuery = filterQuery.bool().must().get(4);
        assertNotNull(publishedQuery.term());
        assertEquals("isPublished", publishedQuery.term().field());
        assertTrue(publishedQuery.term().value().booleanValue());
    }

    @Test
    @DisplayName("autoCompleteProductName should build a prefix query and fetch only the name field")
    void testAutoCompleteProductName_shouldUsePrefixQueryAndNameSourceFilter() {
        SearchHits<Product> searchHits = getSearchHits(buildProduct("Test Product"));
        ArgumentCaptor<NativeQuery> captor = ArgumentCaptor.forClass(NativeQuery.class);

        when(elasticsearchOperations.search(any(NativeQuery.class), eq(Product.class))).thenReturn(searchHits);

        productService.autoCompleteProductName("Prod");

        verify(elasticsearchOperations).search(captor.capture(), eq(Product.class));

        NativeQuery query = captor.getValue();
        assertNotNull(query.getQuery());
        assertNotNull(query.getQuery().matchPhrasePrefix());
        assertEquals("name", query.getQuery().matchPhrasePrefix().field());
        assertEquals("Prod", query.getQuery().matchPhrasePrefix().query());
        assertNotNull(query.getSourceFilter());
        assertEquals(1, query.getSourceFilter().getIncludes().length);
        assertEquals("name", query.getSourceFilter().getIncludes()[0]);
        assertNull(query.getSourceFilter().getExcludes());
    }

    private static void assertTermValues(Query boolQuery, String expectedField, String... expectedValues) {
        assertNotNull(boolQuery.bool());
        assertEquals(expectedValues.length, boolQuery.bool().should().size());

        List<TermQuery> termQueries = boolQuery.bool().should().stream()
            .map(Query::term)
            .toList();

        for (int i = 0; i < expectedValues.length; i++) {
            TermQuery termQuery = termQueries.get(i);
            assertEquals(expectedField, termQuery.field());
            assertEquals(expectedValues[i], termQuery.value().stringValue());
            assertTrue(termQuery.caseInsensitive());
        }
    }

    private static Product buildProduct(String name) {

        return Product.builder()
            .id(1L)
            .name(name)
            .slug("test-product")
            .price(20.0)
            .isPublished(true)
            .isVisibleIndividually(true)
            .isAllowedToOrder(true)
            .isFeatured(true)
            .thumbnailMediaId(123L)
            .categories(List.of("testCategory"))
            .attributes(List.of("testAttribute"))
            .createdOn(ZonedDateTime.now())
            .build();
    }

    private static SearchHits<Product> getSearchHits(Product... products) {
        List<SearchHit<Product>> searchHitList = java.util.Arrays.stream(products)
            .map(product -> new SearchHit<>(
                "products",
                String.valueOf(product.getId()),
                null,
                1.0f,
                null,
                new HashMap<>(),
                new HashMap<>(),
                null,
                null,
                Map.of(),
                product
            ))
            .toList();

        return new SearchHits<>() {

            @Override
            public @NotNull SearchHit<Product> getSearchHit(int index) {
                return searchHitList.get(index);
            }

            @Override
            public AggregationsContainer<?> getAggregations() {
                return null;
            }

            @Override
            public float getMaxScore() {
                return 1;
            }

            @Override
            public Duration getExecutionDuration() {
                return Duration.ZERO;
            }

            @Override
            public @NotNull List<SearchHit<Product>> getSearchHits() {
                return searchHitList;
            }

            @Override
            public long getTotalHits() {
                return searchHitList.size();
            }

            @Override
            public @NotNull TotalHitsRelation getTotalHitsRelation() {
                return TotalHitsRelation.EQUAL_TO;
            }

            @Override
            public Suggest getSuggest() {
                return null;
            }

            @Override
            public String getPointInTimeId() {
                return "";
            }

            @Override
            public SearchShardStatistics getSearchShardStatistics() {
                return null;
            }
        };
    }

}
