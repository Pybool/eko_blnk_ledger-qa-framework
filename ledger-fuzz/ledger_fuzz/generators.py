from hypothesis import strategies as st


precise_amounts = st.one_of(
    st.sampled_from(
        [
            1,
            2**53 - 1,
            2**53,
            2**53 + 1,
            2**63 - 1,
            2**63,
            2**63 + 1,
        ]
    ),
    st.integers(min_value=1, max_value=10**30),
)
