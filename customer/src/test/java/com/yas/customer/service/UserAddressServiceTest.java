package com.yas.customer.service;

import static com.yas.customer.util.SecurityContextUtils.setUpSecurityContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yas.commonlibrary.exception.AccessDeniedException;
import com.yas.commonlibrary.exception.NotFoundException;
import com.yas.customer.model.UserAddress;
import com.yas.customer.repository.UserAddressRepository;
import com.yas.customer.viewmodel.address.ActiveAddressVm;
import com.yas.customer.viewmodel.address.AddressDetailVm;
import com.yas.customer.viewmodel.address.AddressPostVm;
import com.yas.customer.viewmodel.address.AddressVm;
import com.yas.customer.viewmodel.useraddress.UserAddressVm;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserAddressServiceTest {

    private static final String USER_ID = "user-123";

    @Mock
    private UserAddressRepository userAddressRepository;

    @Mock
    private LocationService locationService;

    private UserAddressService userAddressService;

    @BeforeEach
    void setUp() {
        userAddressService = new UserAddressService(userAddressRepository, locationService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getUserAddressList_whenAuthenticated_returnsSortedList() {
        setUpSecurityContext(USER_ID);
        UserAddress ua = UserAddress.builder().id(1L).userId(USER_ID).addressId(100L).isActive(false).build();
        UserAddress uaActive = UserAddress.builder().id(2L).userId(USER_ID).addressId(101L).isActive(true).build();
        when(userAddressRepository.findAllByUserId(USER_ID)).thenReturn(List.of(ua, uaActive));

        AddressDetailVm addr100 = AddressDetailVm.builder()
            .id(100L).contactName("A").phone("1").addressLine1("L1").city("C").zipCode("Z")
            .districtId(1L).districtName("D").stateOrProvinceId(1L).stateOrProvinceName("S").countryId(1L).countryName("Co").build();
        AddressDetailVm addr101 = AddressDetailVm.builder()
            .id(101L).contactName("B").phone("2").addressLine1("L2").city("C2").zipCode("Z2")
            .districtId(2L).districtName("D2").stateOrProvinceId(2L).stateOrProvinceName("S2").countryId(2L).countryName("Co2").build();
        when(locationService.getAddressesByIdList(List.of(100L, 101L))).thenReturn(List.of(addr100, addr101));

        List<ActiveAddressVm> result = userAddressService.getUserAddressList();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).isActive()).isTrue();
        assertThat(result.get(1).isActive()).isFalse();
    }

    @Test
    void getUserAddressList_whenAnonymous_throwsAccessDeniedException() {
        setUpSecurityContext("anonymousUser");
        assertThrows(AccessDeniedException.class, () -> userAddressService.getUserAddressList());
    }

    @Test
    void getAddressDefault_whenAuthenticatedAndHasDefault_returnsAddress() {
        setUpSecurityContext(USER_ID);
        UserAddress ua = UserAddress.builder().id(1L).userId(USER_ID).addressId(50L).isActive(true).build();
        when(userAddressRepository.findByUserIdAndIsActiveTrue(USER_ID)).thenReturn(Optional.of(ua));
        AddressDetailVm detail = AddressDetailVm.builder().id(50L).contactName("X").phone("p").addressLine1("a").city("c").zipCode("z")
            .districtId(1L).districtName("d").stateOrProvinceId(1L).stateOrProvinceName("s").countryId(1L).countryName("co").build();
        when(locationService.getAddressById(50L)).thenReturn(detail);

        AddressDetailVm result = userAddressService.getAddressDefault();

        assertThat(result.id()).isEqualTo(50L);
        assertThat(result.contactName()).isEqualTo("X");
    }

    @Test
    void getAddressDefault_whenAnonymous_throwsAccessDeniedException() {
        setUpSecurityContext("anonymousUser");
        assertThrows(AccessDeniedException.class, () -> userAddressService.getAddressDefault());
    }

    @Test
    void getAddressDefault_whenNoDefaultAddress_throwsNotFoundException() {
        setUpSecurityContext(USER_ID);
        when(userAddressRepository.findByUserIdAndIsActiveTrue(USER_ID)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> userAddressService.getAddressDefault());
    }

    @Test
    void createAddress_whenFirstAddress_setsActiveTrue() {
        setUpSecurityContext(USER_ID);
        when(userAddressRepository.findAllByUserId(USER_ID)).thenReturn(Collections.emptyList());
        AddressPostVm post = new AddressPostVm("Name", "Phone", "Line1", "City", "Zip", 1L, 2L, 3L);
        AddressVm created = AddressVm.builder().id(99L).contactName("Name").phone("Phone").addressLine1("Line1").city("City").zipCode("Zip").districtId(1L).stateOrProvinceId(2L).countryId(3L).build();
        when(locationService.createAddress(post)).thenReturn(created);
        UserAddress saved = UserAddress.builder().id(10L).userId(USER_ID).addressId(99L).isActive(true).build();
        when(userAddressRepository.save(any(UserAddress.class))).thenAnswer(inv -> {
            UserAddress ua = inv.getArgument(0);
            return UserAddress.builder().id(10L).userId(ua.getUserId()).addressId(ua.getAddressId()).isActive(ua.getIsActive()).build();
        });

        UserAddressVm result = userAddressService.createAddress(post);

        assertThat(result.isActive()).isTrue();
        assertThat(result.id()).isEqualTo(10L);
        assertThat(result.userId()).isEqualTo(USER_ID);
    }

    @Test
    void createAddress_whenNotFirstAddress_setsActiveFalse() {
        setUpSecurityContext(USER_ID);
        when(userAddressRepository.findAllByUserId(USER_ID)).thenReturn(List.of(UserAddress.builder().id(1L).userId(USER_ID).addressId(1L).isActive(true).build()));
        AddressPostVm post = new AddressPostVm("N", "P", "L", "C", "Z", 1L, 2L, 3L);
        AddressVm created = AddressVm.builder().id(98L).contactName("N").phone("P").addressLine1("L").city("C").zipCode("Z").districtId(1L).stateOrProvinceId(2L).countryId(3L).build();
        when(locationService.createAddress(post)).thenReturn(created);
        when(userAddressRepository.save(any(UserAddress.class))).thenAnswer(inv -> {
            UserAddress ua = inv.getArgument(0);
            return UserAddress.builder().id(11L).userId(ua.getUserId()).addressId(ua.getAddressId()).isActive(ua.getIsActive()).build();
        });

        UserAddressVm result = userAddressService.createAddress(post);

        assertThat(result.isActive()).isFalse();
    }

    @Test
    void deleteAddress_whenFound_deletes() {
        setUpSecurityContext(USER_ID);
        UserAddress ua = UserAddress.builder().id(1L).userId(USER_ID).addressId(10L).isActive(true).build();
        when(userAddressRepository.findOneByUserIdAndAddressId(USER_ID, 10L)).thenReturn(ua);

        userAddressService.deleteAddress(10L);

        verify(userAddressRepository).delete(ua);
    }

    @Test
    void deleteAddress_whenNotFound_throwsNotFoundException() {
        setUpSecurityContext(USER_ID);
        when(userAddressRepository.findOneByUserIdAndAddressId(USER_ID, 999L)).thenReturn(null);

        assertThrows(NotFoundException.class, () -> userAddressService.deleteAddress(999L));
    }

    @Test
    void chooseDefaultAddress_updatesActiveFlag() {
        setUpSecurityContext(USER_ID);
        UserAddress ua1 = UserAddress.builder().id(1L).userId(USER_ID).addressId(10L).isActive(true).build();
        UserAddress ua2 = UserAddress.builder().id(2L).userId(USER_ID).addressId(20L).isActive(false).build();
        when(userAddressRepository.findAllByUserId(USER_ID)).thenReturn(List.of(ua1, ua2));
        when(userAddressRepository.saveAll(anyList())).thenReturn(List.of());

        userAddressService.chooseDefaultAddress(20L);

        verify(userAddressRepository).saveAll(anyList());
    }
}
