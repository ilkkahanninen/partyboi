const submitBtns = document.querySelectorAll('input[type="submit"]')

submitBtns.forEach(submitBtn => {
    submitBtn.addEventListener('click', () => {
        submitBtn.setAttribute('disabled', 'disabled')
        const progress = document.createElement('progress')
        submitBtn.after(progress)
        submitBtn.form.submit()
    })
})

if (window.Draggable) {
    const sortableSelector = '.sortable'
    const sortableContainers = document.querySelectorAll(sortableSelector)

    sortableContainers.forEach(container => {
        const sortable = new window.Draggable.Sortable(
            container,
            {
                draggable: container.dataset.draggable,
                handle: container.dataset.handle,
                mirror: {
                    appendTo: container,
                    constrainDimensions: true
                }
            }
        )

        const ids = Array.from(container.querySelectorAll(container.dataset.draggable)).map(i => i.dataset.dragid)

        sortable.on('sortable:sorted', (event) => {
            ids.splice(event.newIndex, 0, ...ids.splice(event.oldIndex, 1))
            fetch(container.dataset.callback, {
                method: 'PUT',
                body: JSON.stringify(ids),
                headers: {
                    'Content-Type': 'application/json'
                }
            })
        })
    })
}