package com.forgptstas.neurorecorder.modules.names;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public final class ContextNameRecognitionModuleTest {
    private final ContextNameRecognitionModule module = new ContextNameRecognitionModule();

    @Test
    public void detectsSelfIntroductionWithZovut() {
        assertEquals("Алексей", module.detectSelfIntroduction("Коллеги, меня зовут Алексей, начнём встречу."));
    }

    @Test
    public void detectsSelfIntroductionWithMyName() {
        assertEquals("Анна", module.detectSelfIntroduction("Добрый день, моё имя Анна."));
    }

    @Test
    public void doesNotTreatMentionAsSpeakerName() {
        assertNull(module.detectSelfIntroduction("Позвоните Алексею после встречи."));
    }

    @Test
    public void doesNotTreatCommonVerbAfterYaAsName() {
        assertNull(module.detectSelfIntroduction("Я буду проверять отчёт завтра."));
    }
}
