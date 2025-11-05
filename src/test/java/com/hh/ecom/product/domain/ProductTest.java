package com.hh.ecom.product.domain;

import com.hh.ecom.product.domain.exception.ProductErrorCode;
import com.hh.ecom.product.domain.exception.ProductException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Product 도메인 단위 테스트")
class ProductTest {

    @Nested
    @DisplayName("재고 감소 테스트")
    class DecreaseStockTest {

        @Test
        @DisplayName("재고가 충분할 때 재고 감소에 성공한다")
        void decreaseStock_success() {
            // given
            Product product = Product.create("테스트 상품", "설명", BigDecimal.valueOf(10000), 10);

            // when
            Product result = product.decreaseStock(5);

            // then
            assertThat(result.getStockQuantity()).isEqualTo(5);
            assertThat(result.getUpdatedAt()).isAfter(product.getUpdatedAt());
        }

        @Test
        @DisplayName("재고가 정확히 요청 수량과 같을 때 재고 감소에 성공한다")
        void decreaseStock_exactStock() {
            // given
            Product product = Product.create("테스트 상품", "설명", BigDecimal.valueOf(10000), 10);

            // when
            Product result = product.decreaseStock(10);

            // then
            assertThat(result.getStockQuantity()).isZero();
        }

        @Test
        @DisplayName("재고가 부족할 때 예외가 발생한다")
        void decreaseStock_insufficientStock() {
            // given
            Product product = Product.create("테스트 상품", "설명", BigDecimal.valueOf(10000), 5);

            // when & then
            assertThatThrownBy(() -> product.decreaseStock(10))
                    .isInstanceOf(ProductException.class)
                    .hasMessageContaining("재고가 부족합니다")
                    .extracting("errorCode")
                    .isEqualTo(ProductErrorCode.INSUFFICIENT_STOCK);
        }

        @Test
        @DisplayName("재고가 0일 때 재고 감소 시도 시 예외가 발생한다")
        void decreaseStock_zeroStock() {
            // given
            Product product = Product.create("테스트 상품", "설명", BigDecimal.valueOf(10000), 0);

            // when & then
            assertThatThrownBy(() -> product.decreaseStock(1))
                    .isInstanceOf(ProductException.class)
                    .hasMessageContaining("재고가 부족합니다")
                    .extracting("errorCode")
                    .isEqualTo(ProductErrorCode.INSUFFICIENT_STOCK);
        }
    }

    @Nested
    @DisplayName("판매 가능 여부 확인 테스트")
    class IsAvailableForSaleTest {

        @Test
        @DisplayName("활성 상태이고 재고가 있으면 판매 가능하다")
        void isAvailableForSale_activeAndHasStock() {
            // given
            Product product = Product.create("테스트 상품", "설명", BigDecimal.valueOf(10000), 10);

            // when & then
            assertThat(product.isAvailableForSale()).isTrue();
        }

        @Test
        @DisplayName("재고가 0이면 판매 불가능하다")
        void isAvailableForSale_noStock() {
            // given
            Product product = Product.create("테스트 상품", "설명", BigDecimal.valueOf(10000), 0);

            // when & then
            assertThat(product.isAvailableForSale()).isFalse();
        }

        @Test
        @DisplayName("비활성 상태이면 재고가 있어도 판매 불가능하다")
        void isAvailableForSale_inactive() {
            // given
            Product product = Product.create("테스트 상품", "설명", BigDecimal.valueOf(10000), 10);
            Product deletedProduct = product.softDelete();

            // when & then
            assertThat(deletedProduct.isAvailableForSale()).isFalse();
        }

        @Test
        @DisplayName("비활성 상태이고 재고도 없으면 판매 불가능하다")
        void isAvailableForSale_inactiveAndNoStock() {
            // given
            Product product = Product.create("테스트 상품", "설명", BigDecimal.valueOf(10000), 0);
            Product deletedProduct = product.softDelete();

            // when & then
            assertThat(deletedProduct.isAvailableForSale()).isFalse();
        }
    }

    @Nested
    @DisplayName("재고 충분 여부 확인 테스트")
    class HasEnoughStockTest {

        @Test
        @DisplayName("요청 수량보다 재고가 많으면 충분하다")
        void hasEnoughStock_moreStock() {
            // given
            Product product = Product.create("테스트 상품", "설명", BigDecimal.valueOf(10000), 10);

            // when & then
            assertThat(product.hasEnoughStock(5)).isTrue();
        }

        @Test
        @DisplayName("요청 수량과 재고가 같으면 충분하다")
        void hasEnoughStock_exactStock() {
            // given
            Product product = Product.create("테스트 상품", "설명", BigDecimal.valueOf(10000), 10);

            // when & then
            assertThat(product.hasEnoughStock(10)).isTrue();
        }

        @Test
        @DisplayName("요청 수량보다 재고가 적으면 부족하다")
        void hasEnoughStock_insufficientStock() {
            // given
            Product product = Product.create("테스트 상품", "설명", BigDecimal.valueOf(10000), 5);

            // when & then
            assertThat(product.hasEnoughStock(10)).isFalse();
        }
    }

    @Nested
    @DisplayName("소프트 삭제 및 복원 테스트")
    class SoftDeleteAndRestoreTest {

        @Test
        @DisplayName("소프트 삭제 시 isActive가 false가 되고 deletedAt이 설정된다")
        void softDelete_success() {
            // given
            Product product = Product.create("테스트 상품", "설명", BigDecimal.valueOf(10000), 10);

            // when
            Product deletedProduct = product.softDelete();

            // then
            assertThat(deletedProduct.getIsActive()).isFalse();
            assertThat(deletedProduct.getDeletedAt()).isNotNull();
            assertThat(deletedProduct.getUpdatedAt()).isAfter(product.getUpdatedAt());
        }

        @Test
        @DisplayName("복원 시 isActive가 true가 되고 deletedAt이 null이 된다")
        void restore_success() {
            // given
            Product product = Product.create("테스트 상품", "설명", BigDecimal.valueOf(10000), 10);
            Product deletedProduct = product.softDelete();

            // when
            Product restoredProduct = deletedProduct.restore();

            // then
            assertThat(restoredProduct.getIsActive()).isTrue();
            assertThat(restoredProduct.getDeletedAt()).isNull();
            assertThat(restoredProduct.getUpdatedAt()).isAfter(deletedProduct.getUpdatedAt());
        }

        @Test
        @DisplayName("삭제된 상품을 복원하면 재고가 있어도 판매 가능 상태가 된다")
        void restore_makesProductAvailableForSale() {
            // given
            Product product = Product.create("테스트 상품", "설명", BigDecimal.valueOf(10000), 10);
            Product deletedProduct = product.softDelete();
            assertThat(deletedProduct.isAvailableForSale()).isFalse();

            // when
            Product restoredProduct = deletedProduct.restore();

            // then
            assertThat(restoredProduct.isAvailableForSale()).isTrue();
        }
    }

    @Nested
    @DisplayName("복합 시나리오 테스트")
    class ComplexScenarioTest {

        @Test
        @DisplayName("재고 감소 후에도 판매 가능 여부를 올바르게 판단한다")
        void decreaseStock_andCheckAvailability() {
            // given
            Product product = Product.create("테스트 상품", "설명", BigDecimal.valueOf(10000), 5);
            assertThat(product.isAvailableForSale()).isTrue();

            // when - 재고를 모두 소진
            Product depleted = product.decreaseStock(5);

            // then - 재고가 0이 되어 판매 불가능
            assertThat(depleted.getStockQuantity()).isZero();
            assertThat(depleted.isAvailableForSale()).isFalse();
        }

        @Test
        @DisplayName("재고가 있더라도 삭제된 상품은 재고 감소가 가능하다")
        void softDelete_canStillDecreaseStock() {
            // given
            Product product = Product.create("테스트 상품", "설명", BigDecimal.valueOf(10000), 10);
            Product deletedProduct = product.softDelete();

            // when
            Product result = deletedProduct.decreaseStock(5);

            // then
            assertThat(result.getStockQuantity()).isEqualTo(5);
            assertThat(result.getIsActive()).isFalse();
        }

        @Test
        @DisplayName("불변성 검증: 원본 객체는 변경되지 않는다")
        void immutability_originalNotChanged() {
            // given
            Product original = Product.create("테스트 상품", "설명", BigDecimal.valueOf(10000), 10);
            Integer originalStock = original.getStockQuantity();
            Boolean originalActive = original.getIsActive();

            // when
            Product decreased = original.decreaseStock(3);
            Product deleted = original.softDelete();

            // then - 원본은 변경되지 않음
            assertThat(original.getStockQuantity()).isEqualTo(originalStock);
            assertThat(original.getIsActive()).isEqualTo(originalActive);

            // 새로운 객체들은 변경됨
            assertThat(decreased.getStockQuantity()).isEqualTo(7);
            assertThat(deleted.getIsActive()).isFalse();
        }
    }
}
