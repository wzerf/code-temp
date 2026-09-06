import {App} from 'antd';
import type {MessageInstance} from 'antd/es/message/interface';

let api: Pick<MessageInstance, 'info' | 'success' | 'error' | 'warning' | 'loading' | 'open' | 'destroy'> | null = null;

type Text = string | Parameters<MessageInstance['info']>[0];

type Pending = {
    method: 'info' | 'success' | 'error' | 'warning' | 'loading';
    content: Text;
    duration?: number;
    onClose?: () => void;
};

const pending: Pending[] = [];

export function registerMessageApi(instance: NonNullable<typeof api>) {
    api = instance;
    for (const item of pending.splice(0)) {
        api[item.method](item.content, item.duration, item.onClose);
    }
}

function make(method: 'info' | 'success' | 'error' | 'warning' | 'loading') {
    return (content: Text, duration?: number, onClose?: () => void) => {
        if (api) {
            return api[method](content, duration, onClose);
        }
        pending.push({method, content, duration, onClose});
        return undefined;
    };
}

/** 组件内优先用 App.useApp() 的 message；组件外/回调里用这个代理 */
export const message = {
    info: make('info'),
    success: make('success'),
    error: make('error'),
    warning: make('warning'),
    loading: make('loading'),
    open: (...args: Parameters<MessageInstance['open']>) => (api ? api.open(...args) : undefined),
    destroy: (...args: Parameters<MessageInstance['destroy']>) => api?.destroy(...args),
};

export function FeedbackHolder() {
    const {message: instance} = App.useApp();
    registerMessageApi(instance);
    return null;
}
