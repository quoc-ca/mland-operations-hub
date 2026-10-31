package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto.CartDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailCart;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.CartService;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailActorResolver;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailAccessDeniedException;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailConflictException;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailMemberActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailUnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CartControllerTest {

    private static final String CART_PATH = "/api/v1/members/me/ready-ring-cart";

    @Mock
    private RetailActorResolver actorResolver;

    @Mock
    private CartService cartService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(new CartController(actorResolver, cartService))
            .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
            .setControllerAdvice(new RetailExceptionHandler())
            .build();
    }

    @Test
    void returnsOnlyTheResolvedMembersCart() throws Exception {
        CartDtos.RetailCartView cart = new CartDtos.RetailCartView(
            4L,
            List.of(new CartDtos.CartLineView("line-01", 101L, 10L, 2))
        );
        when(actorResolver.requireActiveMember(any(HttpServletRequest.class))).thenReturn(new RetailMemberActor(99L));
        when(cartService.getCart(99L)).thenReturn(cart);

        mockMvc.perform(get(CART_PATH))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value(4))
            .andExpect(jsonPath("$.lines[0].id").value("line-01"))
            .andExpect(jsonPath("$.lines[0].locationId").value(10));

        verify(cartService).getCart(99L);
    }

    @Test
    void rejectsUnauthenticatedCartAccessWithoutReadingCart() throws Exception {
        when(actorResolver.requireActiveMember(any(HttpServletRequest.class)))
            .thenThrow(new RetailUnauthorizedException("not authenticated"));

        mockMvc.perform(get(CART_PATH))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verifyNoInteractions(cartService);
    }

    @Test
    void rejectsUnauthorizedCartChangesWithoutWritingCart() throws Exception {
        when(actorResolver.requireActiveMember(any(HttpServletRequest.class)))
            .thenThrow(new RetailAccessDeniedException("not an active member"));

        mockMvc.perform(post(CART_PATH + "/lines")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"variantId\":101,\"locationId\":10,\"quantity\":1}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verifyNoInteractions(cartService);
    }

    @Test
    void rejectsAnInvalidAddLineRequestBeforeCallingTheCartService() throws Exception {
        mockMvc.perform(post(CART_PATH + "/lines")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"variantId\":101,\"locationId\":10,\"quantity\":0}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(cartService);
    }

    @Test
    void mapsCartConflictsToAStableConflictResponse() throws Exception {
        CartDtos.AddCartLineRequest request = new CartDtos.AddCartLineRequest(101L, 10L, 1);
        when(actorResolver.requireActiveMember(any(HttpServletRequest.class))).thenReturn(new RetailMemberActor(99L));
        when(cartService.addLine(eq(99L), any(CartDtos.AddCartLineRequest.class)))
            .thenThrow(new RetailConflictException("Cart cannot contain more than 50 lines"));

        mockMvc.perform(post(CART_PATH + "/lines")
                .header("X-Correlation-Id", "cart-conflict-01")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("RETAIL_CONFLICT"))
            .andExpect(jsonPath("$.correlationId").value("cart-conflict-01"));
    }

    @Test
    void mapsAnOptimisticLockFailureToAConflictResponse() throws Exception {
        CartDtos.AddCartLineRequest request = new CartDtos.AddCartLineRequest(101L, 10L, 1);
        when(actorResolver.requireActiveMember(any(HttpServletRequest.class))).thenReturn(new RetailMemberActor(99L));
        when(cartService.addLine(eq(99L), any(CartDtos.AddCartLineRequest.class)))
            .thenThrow(new ObjectOptimisticLockingFailureException(RetailCart.class, "cart-01"));

        mockMvc.perform(post(CART_PATH + "/lines")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("RETAIL_CONFLICT"))
            .andExpect(jsonPath("$.message").value("The cart changed; refresh before retrying"));
    }
}
