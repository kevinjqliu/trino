/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.spi;

import org.junit.jupiter.api.Test;

import static io.trino.spi.ErrorType.USER_ERROR;
import static org.testng.Assert.assertEquals;

public class TestTrinoException
{
    @Test
    public void testMessage()
    {
        TrinoException exception = new TrinoException(new TestErrorCode(), "test");
        assertEquals(exception.getMessage(), "test");

        exception = new TrinoException(new TestErrorCode(), new RuntimeException("test2"));
        assertEquals(exception.getMessage(), "test2");

        exception = new TrinoException(new TestErrorCode(), new RuntimeException());
        assertEquals(exception.getMessage(), "test");
    }

    private static class TestErrorCode
            implements ErrorCodeSupplier
    {
        @Override
        public ErrorCode toErrorCode()
        {
            return new ErrorCode(0, "test", USER_ERROR);
        }
    }
}
