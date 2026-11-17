package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.controller.AccountExceptionHandler;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.CartService;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailActorResolver;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailConflictException;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailMemberActor;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression test for the "An unexpected error occurred" 500 a Member saw when a typed
 * {@link RetailConflictException} was raised during checkout: {@code AccountExceptionHandler} is a catch-all
 * advice registered application-wide, so without an explicit precedence it can resolve the exception before
 * the feature-specific {@link RetailExceptionHandler} does. Both advices are registered here to mirror how
 * Spring assembles them at runtime and to prove the retail advice wins.
 */
class RetailExceptionHandlerPrecedenceTest {

    private static final String CART_LINES_PATH = "/api/v1/members/me/ready-ring-cart/lines";

    private RetailActorResolver actorResolver;
    private CartService cartService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        actorResolver = mock(RetailActorResolver.class);
        cartService = mock(CartService.class);

        mockMvc = MockMvcBuilders.standaloneSetup(new CartController(actorResolver, cartService))
            .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()))
            .setControllerAdvice(new AccountExceptionHandler(), new RetailExceptionHandler())
            .build();

        when(actorResolver.requireActiveMember(any(HttpServletRequest.class))).thenReturn(new RetailMemberActor(7L));
        when(cartService.addLine(eq(7L), any())).thenThrow(new RetailConflictException("Checkout hold expired or is unavailable"));
    }

    @Test
    void retailConflictIsNotSwallowedByTheGlobalCatchAllHandler() throws Exception {
        mockMvc.perform(post(CART_LINES_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"variantId\":101,\"locationId\":10,\"quantity\":1}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("RETAIL_CONFLICT"))
            .andExpect(jsonPath("$.message").value("Checkout hold expired or is unavailable"));
    }
}
