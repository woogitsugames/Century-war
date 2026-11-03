package com.centurywar.game

import org.junit.Test

/** Lustrzany test doktryn z pozycji `index % 4 == 2` w [Doctrine.entries]; opis: [DoctrineBalance82Base]. */
class DoctrineBalance82Mod2Test : DoctrineBalance82Base() {
    @Test fun allDoctrinesStayBetweenFortyAndSixtyFivePercent() = checkDoctrines(2)
}
