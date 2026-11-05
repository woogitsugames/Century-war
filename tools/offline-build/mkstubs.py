"""Zaślepki Play Games (tylko symbole używane w app/src/main) do kompilacji poza Gradle."""
import os, sys
root = sys.argv[1]
files = {
"tasks/Task.java": "package com.google.android.gms.tasks;\npublic abstract class Task<TResult> {\n public abstract boolean isSuccessful();\n public abstract TResult getResult();\n public abstract Task<TResult> addOnCompleteListener(OnCompleteListener<TResult> listener);\n}\n",
"tasks/OnCompleteListener.java": "package com.google.android.gms.tasks;\npublic interface OnCompleteListener<TResult> { void onComplete(Task<TResult> task); }\n",
"games/AuthenticationResult.java": "package com.google.android.gms.games;\npublic class AuthenticationResult { public boolean isAuthenticated() { throw new UnsupportedOperationException(\"stub\"); } }\n",
"games/GamesSignInClient.java": "package com.google.android.gms.games;\nimport com.google.android.gms.tasks.Task;\npublic interface GamesSignInClient { Task<AuthenticationResult> isAuthenticated(); Task<AuthenticationResult> signIn(); }\n",
"games/LeaderboardsClient.java": "package com.google.android.gms.games;\nimport com.google.android.gms.tasks.Task;\npublic interface LeaderboardsClient { void submitScore(String leaderboardId, long score); Task<android.content.Intent> getLeaderboardIntent(String leaderboardId, int timeSpan); }\n",
"games/AchievementsClient.java": "package com.google.android.gms.games;\npublic interface AchievementsClient { void unlock(String id); }\n",
"games/SnapshotsClient.java": "package com.google.android.gms.games;\nimport com.google.android.gms.tasks.Task;\nimport com.google.android.gms.games.snapshot.*;\npublic interface SnapshotsClient {\n int RESOLUTION_POLICY_MANUAL = -1;\n Task<DataOrConflict<Snapshot>> open(String fileName, boolean createIfNotFound, int conflictPolicy);\n Task<DataOrConflict<Snapshot>> resolveConflict(String conflictId, Snapshot snapshot);\n Task<Void> discardAndClose(Snapshot snapshot);\n Task<SnapshotMetadata> commitAndClose(Snapshot snapshot, SnapshotMetadataChange metadataChange);\n class DataOrConflict<T> { public boolean isConflict() { throw new UnsupportedOperationException(); } public T getData() { throw new UnsupportedOperationException(); } public SnapshotConflict getConflict() { throw new UnsupportedOperationException(); } }\n class SnapshotConflict { public Snapshot getSnapshot() { throw new UnsupportedOperationException(); } public Snapshot getConflictingSnapshot() { throw new UnsupportedOperationException(); } public String getConflictId() { throw new UnsupportedOperationException(); } }\n}\n",
"games/PlayGames.java": "package com.google.android.gms.games;\nimport android.app.Activity;\npublic final class PlayGames {\n public static GamesSignInClient getGamesSignInClient(Activity a) { throw new UnsupportedOperationException(\"stub\"); }\n public static LeaderboardsClient getLeaderboardsClient(Activity a) { throw new UnsupportedOperationException(\"stub\"); }\n public static AchievementsClient getAchievementsClient(Activity a) { throw new UnsupportedOperationException(\"stub\"); }\n public static SnapshotsClient getSnapshotsClient(Activity a) { throw new UnsupportedOperationException(\"stub\"); }\n}\n",
"games/PlayGamesSdk.java": "package com.google.android.gms.games;\npublic final class PlayGamesSdk { public static void initialize(android.content.Context c) { throw new UnsupportedOperationException(\"stub\"); } }\n",
"games/leaderboard/LeaderboardVariant.java": "package com.google.android.gms.games.leaderboard;\npublic interface LeaderboardVariant { int TIME_SPAN_DAILY = 0; int TIME_SPAN_WEEKLY = 1; int TIME_SPAN_ALL_TIME = 2; }\n",
"games/snapshot/Snapshot.java": "package com.google.android.gms.games.snapshot;\npublic interface Snapshot { SnapshotContents getSnapshotContents(); }\n",
"games/snapshot/SnapshotContents.java": "package com.google.android.gms.games.snapshot;\npublic interface SnapshotContents { byte[] readFully() throws java.io.IOException; boolean writeBytes(byte[] content); }\n",
"games/snapshot/SnapshotMetadata.java": "package com.google.android.gms.games.snapshot;\npublic interface SnapshotMetadata {}\n",
"games/snapshot/SnapshotMetadataChange.java": "package com.google.android.gms.games.snapshot;\npublic abstract class SnapshotMetadataChange {\n public static final class Builder {\n  public Builder setProgressValue(long v) { return this; }\n  public Builder setDescription(String d) { return this; }\n  public SnapshotMetadataChange build() { return new SnapshotMetadataChange() {}; }\n }\n}\n",
}
for p, c in files.items():
    path = os.path.join(root, "com/google/android/gms", p)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, "w").write(c)
print(len(files))
