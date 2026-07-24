# Project Creation and Notification Hardening Design

## Goal

Improve the existing five-step project creation wizard without rebuilding its architecture. The change must make notification delivery idempotent, prevent duplicate rules, preserve task-stage relationships, expose useful previews and risks, and fix the three reported regressions in project dates, change submission, and project-card menus.

Import and export are explicitly out of scope.

## Confirmed Product Decisions

- Keep the existing five-step wizard.
- Calculate task duration in natural days: `end date - start date + 1`.
- Unassigned task owners are warnings, not blockers.
- Use database-backed notification idempotency.
- A repeated request in the same dispatch batch must not resend.
- A withdrawal followed by a new dispatch creates a new batch and may send again.
- Remove the two ineffective checkboxes for notifying all members and generating a Gantt chart.
- The server, not the client, determines the change initiator from the authenticated user.

## Notification Rule Deduplication

A rule is considered a duplicate inside the same notification mode or project when these normalized fields match:

- trigger event
- notification target
- notification channel

Mode create and update must reject duplicates with a specific validation message. Project creation must defensively copy only distinct rules so legacy duplicate mode data cannot create duplicate project rules.

The existing duplicate `task_dispatched -> main_owner -> dingtalk` rule in mode `1` will be removed through a migration. A database constraint or equivalent service-level guard will prevent it from returning.

## Notification Delivery Idempotency

Notification logs will store an idempotency key protected by a unique index. The key identifies:

- business type and business ID
- trigger event
- event batch
- receiver
- channel

For dispatch, each valid dispatch transition creates a new dispatch batch. Concurrent or repeated calls in the same batch reserve the same idempotency key, so only one request may send. A withdrawal and later redispatch increments the batch and produces a new key.

Failed sends must not be recorded as successful. Retry behavior must remain possible for a failed batch without allowing two concurrent successful sends.

## Five-Step Wizard

### 1. Template Selection

Template preview uses the same loaded stage and task collection that will be submitted. It shows stage, task name, responsible role, dates, and calculated duration. A template with no tasks is a blocking risk.

### 2. Project Information

Project department, start date, and end date are required. End date cannot precede start date. The same validation runs in the backend so invalid API requests are rejected.

### 3. Project Members

Replace the collapsed multi-select display with a member detail table:

- name
- department
- project role
- action

The project manager is automatically included, pinned first, marked as project manager, and cannot be removed. Other members can be searched, added, and removed.

### 4. Task Adjustment

The stage selector binds directly to `stageId`; stage name is display-only. New tasks therefore persist a valid stage relationship.

Duration is read-only and calculated from task start and end dates using inclusive natural days. Manual duration entry and batch duration editing are removed. Batch date adjustment remains.

### 5. Rules and Confirmation

Selecting a notification mode displays its effective rules with trigger, target, channel, and enabled state. Duplicate or invalid rules are visibly identified.

Remove the ineffective checkboxes:

- notify all project members after creation
- generate Gantt chart after creation

The final page displays a risk checklist.

Blocking risks:

- project end date earlier than start date
- template has no tasks
- task has no valid stage ID
- invalid task date range or task date outside project period
- task owner or helper is not a project member
- notification mode has no effective rules
- notification mode contains duplicate rules

Warnings:

- task has no owner
- task has no output requirement
- project has no milestone task

Blocking risks disable project submission and link the user back to the relevant step.

## Reported Regression Fixes

### Change Submission

Remove client-supplied `initiatorId`. The backend populates the initiator from the authenticated login context. The UI displays the backend error message when submission fails instead of replacing it with a generic message.

### Project Card Menu

Move click propagation prevention to a dedicated trigger wrapper around the dropdown. Keep the dropdown popper attached to the page root so its position is calculated from the visible three-dot trigger rather than the viewport origin.

### Project Date Validation

Add cross-field validation to the project form and equivalent service validation for create and update operations.

## Verification

Automated tests must cover:

- duplicate mode rules are rejected
- project rule snapshots are deduplicated
- one dispatch batch creates one successful notification
- redispatch after withdrawal creates a new notification batch
- invalid project date ranges are rejected
- authenticated user becomes the change initiator
- new tasks persist a valid stage ID
- inclusive natural-day duration for same-day and multi-day ranges

UI regression testing must cover the complete five-step creation flow, rule preview, risk blockers and warnings, member table, template preview, automatic duration, change submission, correctly positioned card menus, one notification per dispatch batch, and a new notification after withdrawal and redispatch.

## Scope Boundaries

- No task import or export.
- No workday calendar.
- No single-page wizard redesign.
- No unrelated PMS refactoring.
