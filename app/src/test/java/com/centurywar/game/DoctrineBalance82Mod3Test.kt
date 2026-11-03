package com.centurywar.game

import org.junit.Test

/** Lustrzany test doktryn z pozycji `index % 4 == 3` w [Doctrine.entries]; opis: [DoctrineBalance82Base]. */
class DoctrineBalance82Mod3Test : DoctrineBalance82Base() {
    @Test fun allDoctrinesStayBetweenFortyAndSixtyFivePercent() = checkDoctrines(3)
}
