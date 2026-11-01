# Komponenty tworzone przez system Android na podstawie manifestu.
# AGP generuje reguly manifestu, ale zachowujemy je jawnie dla kontroli DEX.
-keep class com.centurywar.CenturyWarApp { public <init>(); }
-keep class com.centurywar.MainActivity { public <init>(); }
-keep class com.centurywar.meta.ReplayShareProvider { public <init>(); }
-keep class com.centurywar.notifications.DailyReminderReceiver { public <init>(); }

# Identyfikatory enumow w snapshotach i powtorkach sa ordinalami.
# Metryki osiagniec sa dodatkowo zapisywane do JSON po nazwie.
# Nie pozwalamy R8 usunac/unboxowac tych klas ani zmienic ich stalych.
-keep enum com.centurywar.meta.AchievementMetric { *; }
-keep enum com.centurywar.game.AiPersonality { *; }
-keep enum com.centurywar.game.DailyEvent { *; }
-keep enum com.centurywar.game.Doctrine { *; }
-keep enum com.centurywar.game.EndlessCard { *; }
-keep enum com.centurywar.game.EndlessWaveTheme { *; }
-keep enum com.centurywar.game.EraArt { *; }
-keep enum com.centurywar.game.Mode { *; }
-keep enum com.centurywar.game.Modifier { *; }
-keep enum com.centurywar.game.Replay$Kind { *; }
-keep enum com.centurywar.game.Role { *; }
-keep enum com.centurywar.game.Weather { *; }

# Play Games jest wywolywane bezposrednio przez SDK; reguly bibliotek
# pochodza z ich consumer ProGuard. Brak w projekcie BackupAgent.
# Nie dodajemy szerokiego -keep com.centurywar.**
