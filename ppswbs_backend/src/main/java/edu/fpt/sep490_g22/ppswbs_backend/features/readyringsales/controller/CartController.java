package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto.CartDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.CartService;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailActorResolver;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailMemberActor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/members/me/ready-ring-cart")
@RequiredArgsConstructor
public class CartController {

    private final RetailActorResolver actorResolver;
    private final CartService cartService;

    @GetMapping
    public CartDtos.RetailCartView getCart(HttpServletRequest request) {
        RetailMemberActor actor = actorResolver.requireActiveMember(request);
        return cartService.getCart(actor.accountId());
    }

    @PostMapping("/lines")
    public CartDtos.RetailCartView addLine(
        HttpServletRequest request,
        @Valid @RequestBody CartDtos.AddCartLineRequest body
    ) {
        RetailMemberActor actor = actorResolver.requireActiveMember(request);
        return cartService.addLine(actor.accountId(), body);
    }

    @PatchMapping("/lines/{lineId}")
    public CartDtos.RetailCartView updateLine(
        HttpServletRequest request,
        @PathVariable String lineId,
        @Valid @RequestBody CartDtos.UpdateCartLineRequest body
    ) {
        RetailMemberActor actor = actorResolver.requireActiveMember(request);
        return cartService.updateLine(actor.accountId(), lineId, body);
    }

    @DeleteMapping("/lines/{lineId}")
    public ResponseEntity<Void> removeLine(HttpServletRequest request, @PathVariable String lineId) {
        RetailMemberActor actor = actorResolver.requireActiveMember(request);
        cartService.removeLine(actor.accountId(), lineId);
        return ResponseEntity.noContent().build();
    }
}
