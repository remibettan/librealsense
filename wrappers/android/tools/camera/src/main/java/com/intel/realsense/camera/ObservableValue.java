package com.intel.realsense.camera;

public class ObservableValue<T> {

    private OnValueChangeListener<T> mListener;
    private T mValue;

    public void setOnValueChangeListener(OnValueChangeListener<T> listener) {
        this.mListener = listener;
    }

    public T get() {
        return mValue;
    }

    public void set(T value) {
        this.mValue = value;

        if(mListener != null) {
            mListener.onValueChanged(value);
        }
    }
}
