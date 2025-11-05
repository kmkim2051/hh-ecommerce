package com.hh.ecom.product.application;

import com.hh.ecom.product.domain.Product;
import com.hh.ecom.product.domain.ProductRepository;
import com.hh.ecom.product.domain.exception.ProductErrorCode;
import com.hh.ecom.product.domain.exception.ProductException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService 단위 테스트")
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    private Product testProduct;

    @BeforeEach
    void setUp() {
        testProduct = Product.create(
                "테스트 상품",
                "상품 설명",
                BigDecimal.valueOf(10000),
                100
        );
    }

    @Nested
    @DisplayName("상품 목록 조회 테스트")
    class GetProductListTest {

        @Test
        @DisplayName("페이지네이션을 적용하여 상품 목록을 조회한다")
        void getProductList_withPagination() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            List<Product> products = List.of(
                    Product.create("상품1", "설명1", BigDecimal.valueOf(1000), 10),
                    Product.create("상품2", "설명2", BigDecimal.valueOf(2000), 20)
            );
            Page<Product> expectedPage = new PageImpl<>(products, pageable, products.size());

            given(productRepository.findAll(any(Pageable.class))).willReturn(expectedPage);

            // when
            Page<Product> result = productService.getProductList(pageable);

            // then
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getNumber()).isEqualTo(0);
            assertThat(result.getSize()).isEqualTo(10);
            verify(productRepository).findAll(pageable);
        }

        @Test
        @DisplayName("빈 페이지를 반환할 수 있다")
        void getProductList_emptyPage() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            Page<Product> emptyPage = Page.empty(pageable);

            given(productRepository.findAll(any(Pageable.class))).willReturn(emptyPage);

            // when
            Page<Product> result = productService.getProductList(pageable);

            // then
            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
            verify(productRepository).findAll(pageable);
        }
    }

    @Nested
    @DisplayName("상품 상세 조회 테스트")
    class GetProductTest {

        @Test
        @DisplayName("ID로 상품을 조회한다")
        void getProduct_success() {
            // given
            Long productId = 1L;
            given(productRepository.findById(anyLong())).willReturn(Optional.of(testProduct));

            // when
            Product result = productService.getProduct(productId);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("테스트 상품");
            verify(productRepository).findById(productId);
        }

        @Test
        @DisplayName("존재하지 않는 ID로 조회 시 예외가 발생한다")
        void getProduct_notFound() {
            // given
            Long productId = 999L;
            given(productRepository.findById(anyLong())).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> productService.getProduct(productId))
                    .isInstanceOf(ProductException.class)
                    .extracting("errorCode")
                    .isEqualTo(ProductErrorCode.PRODUCT_NOT_FOUND);

            verify(productRepository).findById(productId);
        }
    }

    @Nested
    @DisplayName("상품 재고 조회 테스트")
    class GetProductStockTest {

        @Test
        @DisplayName("ID로 상품 재고를 조회한다")
        void getProductStock_success() {
            // given
            Long productId = 1L;
            given(productRepository.findById(anyLong())).willReturn(Optional.of(testProduct));

            // when
            Product result = productService.getProductStock(productId);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getStockQuantity()).isEqualTo(100);
            verify(productRepository).findById(productId);
        }

        @Test
        @DisplayName("존재하지 않는 상품의 재고 조회 시 예외가 발생한다")
        void getProductStock_notFound() {
            // given
            Long productId = 999L;
            given(productRepository.findById(anyLong())).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> productService.getProductStock(productId))
                    .isInstanceOf(ProductException.class)
                    .extracting("errorCode")
                    .isEqualTo(ProductErrorCode.PRODUCT_NOT_FOUND);

            verify(productRepository).findById(productId);
        }
    }

    @Nested
    @DisplayName("조회수 기반 상품 목록 조회 테스트")
    class GetProductsByViewCountTest {

        @Test
        @DisplayName("조회수가 높은 상품 목록을 조회한다")
        void getProductsByViewCount_success() {
            // given
            Integer limit = 5;
            Product product1 = Product.create("상품1", "설명1", BigDecimal.valueOf(1000), 10);
            Product product2 = Product.create("상품2", "설명2", BigDecimal.valueOf(2000), 20);
            Product product3 = Product.create("상품3", "설명3", BigDecimal.valueOf(3000), 30);

            // 조회수를 증가시켜 정렬 순서 테스트
            product1 = product1.increaseViewCount().increaseViewCount().increaseViewCount();
            product2 = product2.increaseViewCount().increaseViewCount();
            product3 = product3.increaseViewCount();

            List<Product> topProducts = List.of(product1, product2, product3);
            given(productRepository.findTopByViewCount(anyInt())).willReturn(topProducts);

            // when
            List<Product> result = productService.getProductsByViewCount(limit);

            // then
            assertThat(result).hasSize(3);
            assertThat(result.get(0).getViewCount()).isGreaterThanOrEqualTo(result.get(1).getViewCount());
            assertThat(result.get(1).getViewCount()).isGreaterThanOrEqualTo(result.get(2).getViewCount());
            verify(productRepository).findTopByViewCount(limit);
        }

        @Test
        @DisplayName("조회수 기반 조회 시 빈 리스트를 반환할 수 있다")
        void getProductsByViewCount_emptyList() {
            // given
            Integer limit = 5;
            given(productRepository.findTopByViewCount(anyInt())).willReturn(List.of());

            // when
            List<Product> result = productService.getProductsByViewCount(limit);

            // then
            assertThat(result).isEmpty();
            verify(productRepository).findTopByViewCount(limit);
        }

        @Test
        @DisplayName("limit 만큼만 상품을 조회한다")
        void getProductsByViewCount_withLimit() {
            // given
            Integer limit = 3;
            List<Product> products = List.of(
                    Product.create("상품1", "설명1", BigDecimal.valueOf(1000), 10),
                    Product.create("상품2", "설명2", BigDecimal.valueOf(2000), 20),
                    Product.create("상품3", "설명3", BigDecimal.valueOf(3000), 30)
            );
            given(productRepository.findTopByViewCount(anyInt())).willReturn(products);

            // when
            List<Product> result = productService.getProductsByViewCount(limit);

            // then
            assertThat(result).hasSize(limit);
            verify(productRepository).findTopByViewCount(limit);
        }
    }

    @Nested
    @DisplayName("판매량 기반 상품 목록 조회 테스트")
    class GetProductsBySalesCountTest {

        @Test
        @DisplayName("판매량이 높은 상품 목록을 조회한다")
        void getProductsBySalesCount_success() {
            // given
            Integer limit = 5;
            List<Product> topProducts = List.of(
                    Product.create("인기상품1", "설명1", BigDecimal.valueOf(10000), 50),
                    Product.create("인기상품2", "설명2", BigDecimal.valueOf(20000), 30),
                    Product.create("인기상품3", "설명3", BigDecimal.valueOf(30000), 20)
            );
            given(productRepository.findTopBySalesCount(anyInt())).willReturn(topProducts);

            // when
            List<Product> result = productService.getProductsBySalesCount(limit);

            // then
            assertThat(result).hasSize(3);
            assertThat(result).extracting("name")
                    .containsExactly("인기상품1", "인기상품2", "인기상품3");
            verify(productRepository).findTopBySalesCount(limit);
        }

        @Test
        @DisplayName("판매량 기반 조회 시 빈 리스트를 반환할 수 있다")
        void getProductsBySalesCount_emptyList() {
            // given
            Integer limit = 5;
            given(productRepository.findTopBySalesCount(anyInt())).willReturn(List.of());

            // when
            List<Product> result = productService.getProductsBySalesCount(limit);

            // then
            assertThat(result).isEmpty();
            verify(productRepository).findTopBySalesCount(limit);
        }

        @Test
        @DisplayName("limit 만큼만 상품을 조회한다")
        void getProductsBySalesCount_withLimit() {
            // given
            Integer limit = 2;
            List<Product> products = List.of(
                    Product.create("상품1", "설명1", BigDecimal.valueOf(1000), 10),
                    Product.create("상품2", "설명2", BigDecimal.valueOf(2000), 20)
            );
            given(productRepository.findTopBySalesCount(anyInt())).willReturn(products);

            // when
            List<Product> result = productService.getProductsBySalesCount(limit);

            // then
            assertThat(result).hasSize(limit);
            verify(productRepository).findTopBySalesCount(limit);
        }
    }
}
