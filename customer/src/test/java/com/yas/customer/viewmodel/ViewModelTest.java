package com.yas.customer.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import com.yas.customer.model.UserAddress;
import com.yas.customer.viewmodel.customer.CustomerAdminVm;
import com.yas.customer.viewmodel.customer.CustomerVm;
import com.yas.customer.viewmodel.useraddress.UserAddressVm;
import com.yas.customer.viewmodel.address.AddressVm;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.UserRepresentation;

class ViewModelTest {

    @Test
    void customerAdminVm_fromUserRepresentation_mapsAllFields() {
        UserRepresentation ur = new UserRepresentation();
        ur.setId("id1");
        ur.setUsername("user1");
        ur.setEmail("u1@example.com");
        ur.setFirstName("First");
        ur.setLastName("Last");
        ur.setCreatedTimestamp(946684800000L);

        CustomerAdminVm vm = CustomerAdminVm.fromUserRepresentation(ur);

        assertThat(vm.id()).isEqualTo("id1");
        assertThat(vm.username()).isEqualTo("user1");
        assertThat(vm.email()).isEqualTo("u1@example.com");
        assertThat(vm.firstName()).isEqualTo("First");
        assertThat(vm.lastName()).isEqualTo("Last");
        assertThat(vm.createdTimestamp()).isNotNull();
    }

    @Test
    void customerVm_fromUserRepresentation_mapsAllFields() {
        UserRepresentation ur = new UserRepresentation();
        ur.setId("id2");
        ur.setUsername("user2");
        ur.setEmail("u2@example.com");
        ur.setFirstName("A");
        ur.setLastName("B");

        CustomerVm vm = CustomerVm.fromUserRepresentation(ur);

        assertThat(vm.id()).isEqualTo("id2");
        assertThat(vm.username()).isEqualTo("user2");
        assertThat(vm.email()).isEqualTo("u2@example.com");
        assertThat(vm.firstName()).isEqualTo("A");
        assertThat(vm.lastName()).isEqualTo("B");
    }

    @Test
    void userAddressVm_fromModel_mapsAllFields() {
        UserAddress model = UserAddress.builder()
            .id(1L)
            .userId("uid")
            .addressId(100L)
            .isActive(true)
            .build();
        AddressVm addressVm = AddressVm.builder()
            .id(100L)
            .contactName("X")
            .phone("p")
            .addressLine1("line")
            .city("city")
            .zipCode("zip")
            .districtId(1L)
            .stateOrProvinceId(2L)
            .countryId(3L)
            .build();

        UserAddressVm vm = UserAddressVm.fromModel(model, addressVm);

        assertThat(vm.id()).isEqualTo(1L);
        assertThat(vm.userId()).isEqualTo("uid");
        assertThat(vm.isActive()).isTrue();
        assertThat(vm.addressGetVm()).isEqualTo(addressVm);
    }
}
