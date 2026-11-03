package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

public record PublishedPackageSelectionSnapshot(
        String packageReference,
        long publicPerParticipantPrice,
        int minParticipants,
        int maxParticipants,
        int requiredSessionCount,
        boolean sameLocationRequired,
        int minimumSameDaySlotGap,
        int requiredFoundryWorkingDaysBetweenSessions,
        String packageName) {

    public PublishedPackageSelectionSnapshot(String packageReference, long publicPerParticipantPrice,
                                             int minParticipants, int maxParticipants, int requiredSessionCount,
                                             boolean sameLocationRequired, int minimumSameDaySlotGap,
                                             String packageName) {
        this(packageReference, publicPerParticipantPrice, minParticipants, maxParticipants, requiredSessionCount,
                sameLocationRequired, minimumSameDaySlotGap, 0, packageName);
    }

    public PublishedPackageSelectionSnapshot(String packageReference, long publicPerParticipantPrice,
                                             int minParticipants, int maxParticipants, int requiredSessionCount,
                                             boolean sameLocationRequired, int minimumSameDaySlotGap) {
        this(packageReference, publicPerParticipantPrice, minParticipants, maxParticipants, requiredSessionCount,
                sameLocationRequired, minimumSameDaySlotGap, 0, null);
    }
}
