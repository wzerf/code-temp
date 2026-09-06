import {AppRouter} from '@/router';
import {useLocaleSync} from '@/core/i18n/hooks/useLocaleSync';
import {FeedbackHolder} from '@/core/feedback/message';

function App() {
    // 同步 preferences 和 i18n 语言
    useLocaleSync();

    return (
        <>
            <FeedbackHolder />
            <AppRouter />
        </>
    );
}

export default App;
