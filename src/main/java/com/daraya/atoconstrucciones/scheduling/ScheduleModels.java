package com.daraya.atoconstrucciones.scheduling;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public final class ScheduleModels {
    private ScheduleModels() {}
    public record TaskInput(@NotNull UUID id, UUID parentId, @NotNull @Pattern(regexp="LEAF|SUMMARY|MILESTONE") String kind,
        @NotBlank @Size(max=160) String title, LocalDate startDate, LocalDate endExclusive,
        @NotNull @Pattern(regexp="PLANNED|IN_PROGRESS|BLOCKED|COMPLETED|CANCELLED") String state,
        boolean administrative, boolean clientVisible, UUID extraId, @NotNull @Size(max=20) List<@NotNull UUID> supplierIds) {}
    public record Dependency(@NotNull UUID predecessorId,@NotNull UUID successorId,@NotNull @Pattern(regexp="FS") String kind,
        @Min(0) @Max(3650) int lagDays) {}
    public record Changes(@Min(1) long baseRevision,@NotNull @Size(max=500) List<@Valid TaskInput> tasks,
        @NotNull @Size(max=2000) List<@Valid Dependency> dependencies,@NotBlank @Size(max=1000) String reason) {}
    public record Task(UUID id,UUID parentId,String kind,String title,LocalDate startDate,LocalDate endExclusive,String state,
        boolean administrative,boolean clientVisible,UUID extraId,List<UUID> supplierIds,String verifiedPercent,LocalDate verifiedOn,Instant verifiedAt) {}
    public record Schedule(UUID projectId,long revision,List<Task> tasks,List<Dependency> dependencies,List<Baseline> baselines,List<Report> reports,List<UUID> progressTaskIds) {}
    public record ReportInput(@NotBlank @Pattern(regexp="[0-9]{1,3}(\\.[0-9]{1,4})?") String percent,
        @NotNull LocalDate observedOn,@NotNull UUID evidenceId,@NotBlank @Size(max=1000) String note) {}
    public record Verification(@NotBlank @Pattern(regexp="[0-9]{1,3}(\\.[0-9]{1,4})?") String percent,
        @NotNull UUID evidenceId,@NotBlank @Size(max=1000) String reason) {}
    public record Report(UUID id,UUID taskId,String reportedPercent,LocalDate observedOn,UUID evidenceId,String note,
        UUID reportedBy,Instant recordedAt,String verifiedPercent,Instant verifiedAt) {}
    public record Weight(@NotNull UUID taskId,@NotBlank @Pattern(regexp="[0-9]{1,3}(\\.[0-9]{1,6})?") String weight,
        @NotNull @Size(max=1000) String exclusionReason) {}
    public record BaselineInput(@NotNull @Size(max=500) List<@Valid Weight> weights,@NotBlank @Size(max=1000) String reason) {}
    public record Decision(@NotBlank @Size(max=1000) String reason) {}
    public record Baseline(UUID id,List<Weight> weights,String reason,String state,Long approvedRevision,String previousPercent,String percent) {}
    public record Milestone(String title,LocalDate estimatedOn,String verifiedPercent,boolean delayed) {}
    public record ProgressSummary(String status,String percent,Long baselineRevision,long scheduleRevision,Instant asOf,
        Instant lastVerifiedAt,LocalDate estimatedEndsOn,String reason,String previousPercent,List<Milestone> milestones) {}
    public record AssignedTask(UUID id,String title,String kind,LocalDate startDate,LocalDate endExclusive,String state,
        String verifiedPercent,LocalDate earliestStart) {}
    public record AssignedTasks(long revision,List<AssignedTask> items,List<Report> reports) {}
}
