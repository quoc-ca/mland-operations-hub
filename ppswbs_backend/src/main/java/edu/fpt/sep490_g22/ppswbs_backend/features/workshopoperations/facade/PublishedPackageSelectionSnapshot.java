package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

public record PublishedPackageSelectionSnapshot(
        String packageReference,
        long publicPerParticipantPrice,
        int minParticipants,
        int maxParticipants,
        int requiredSessionCount,
        boolean sameLocationRequired,
        int minimumSameDaySlotGap,
        String packageName) {

    public PublishedPackageSelectionSnapshot(String packageReference, long publicPerParticipantPrice,
                                             int minParticipants, int maxParticipants, int requiredSessionCount,
                                             boolean sameLocationRequired, int minimumSameDaySlotGap) {
        this(packageReference, publicPerParticipantPrice, minParticipants, maxParticipants, requiredSessionCount,
                sameLocationRequired, minimumSameDaySlotGap, null);
    }
}
