package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DesignOptionsResponseDto {

    private String packageReference;

    private String rulesVersion;

    @Builder.Default
    private List<MaterialOptionDto> materials = new ArrayList<>();

    @Builder.Default
    private List<ShapeOptionDto> shapes = new ArrayList<>();

    @Builder.Default
    private List<String> sizes = new ArrayList<>();

    @Builder.Default
    private List<ComponentOptionDto> components = new ArrayList<>();

    @Builder.Default
    private List<AttachmentOptionDto> attachments = new ArrayList<>();

    private EngravingConfigDto engravingConfig;

    @Builder.Default
    private List<ModelOptionDto> models = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MaterialOptionDto {
        private String reference;
        private String name;
        private String description;
        private String purity;
        private String unitPrice;
        private String currency;
        private String difficultyScore;
        private String imageUrl;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShapeOptionDto {
        private String reference;
        private String name;
        private String description;
        private String priceAdjustment;
        private String currency;
        private String difficultyScore;
        private String imageUrl;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ComponentOptionDto {
        private String reference;
        private String name;
        private String description;
        private String type;
        private String color;
        private String cutName;
        private String unitPrice;
        private String currency;
        private String difficultyScore;
        private String imageUrl;
        private Integer maxQuantity;
        private List<String> allowedPlacements;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AttachmentOptionDto {
        private String reference;
        private String name;
        private String description;
        private String type;
        private String unitPrice;
        private String currency;
        private String difficultyScore;
        private String imageUrl;
        private Integer maxQuantity;
        private List<String> allowedPlacements;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EngravingConfigDto {
        private Boolean supported;
        private Integer maxCharacters;
        private List<String> allowedPlacements;
        private String priceAdjustment;
        private String currency;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ModelOptionDto {
        private String modelReference;
        private String name;
        private String description;
        private String imageUrl;
        private String materialReference;
        private String shapeReference;
    }
}

