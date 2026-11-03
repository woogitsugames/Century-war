package com.centurywar.game

import org.junit.Test

/** Lustrzany test doktryn z pozycji `index % 4 == 0` w [Doctrine.entries]; opis: [DoctrineBalance82Base]. */
class DoctrineBalance82Test : DoctrineBalance82Base() {
    @Test fun allDoctrinesStayBetweenFortyAndSixtyFivePercent() = checkDoctrines(0)
}
