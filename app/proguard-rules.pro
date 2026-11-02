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
-keep enum com.centurywar.game.CampaignDifficulty { *; }
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

# Kontrakt formatu: Profile zapisuje jawny JSON, BattleSnapshot i ReplayRecord
# zapisuja bajty, a Replay koduje komendy. Nie uzywamy serializacji refleksyjnej.
# Chronimy nazwy klas i ich pol, lecz pozostawiamy R8 optymalizacje metod.
# CI sprawdza wymagane pola na podstawie mapping.txt i usage.txt.
-keep class com.centurywar.meta.Profile { <fields>; }
-keep class com.centurywar.meta.ScoreEntry { <fields>; }
-keep class com.centurywar.game.BattleSnapshot { <fields>; }
-keep class com.centurywar.game.RestoredBattle { <fields>; }
-keep class com.centurywar.game.SnapshotStore { <fields>; }
-keep class com.centurywar.game.BattleConfig { <fields>; }
-keep class com.centurywar.game.Bonuses { <fields>; }
-keep class com.centurywar.game.Replay { <fields>; }
-keep class com.centurywar.game.Replay$Command { <fields>; }
-keep class com.centurywar.game.Replay$Log { <fields>; }
-keep class com.centurywar.game.ReplayRecord { <fields>; }
-keep class com.centurywar.game.ReplayRecord$Meta { <fields>; }
-keep class com.centurywar.game.ReplayRecord$Record { <fields>; }
