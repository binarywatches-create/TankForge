extends Control

var status_label: Label

func _ready() -> void:
    status_label = Label.new()
    status_label.anchor_left = 0.1
    status_label.anchor_top = 0.35
    status_label.anchor_right = 0.9
    status_label.anchor_bottom = 0.75
    status_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
    status_label.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
    status_label.add_theme_font_size_override("font_size", 30)
    status_label.add_theme_color_override("font_color", Color.WHITE)
    add_child(status_label)
    status_label.text = "DISPLAY OK\\nLoading battlefield scene..."
    await get_tree().create_timer(2.0).timeout

    var packed = load("res://scenes/battlefield.tscn")
    if packed == null:
        status_label.text = "ERROR: battlefield.tscn failed to load"
        return
    status_label.text = "SCENE RESOURCE LOADED\\nCreating battlefield instance..."
    await get_tree().create_timer(2.0).timeout

    var instance = packed.instantiate()
    if instance == null:
        status_label.text = "ERROR: battlefield instance is null"
        return
    status_label.text = "INSTANCE CREATED\\nAdding battlefield to scene tree..."
    await get_tree().create_timer(2.0).timeout
    add_child(instance)
    status_label.text = "BATTLEFIELD ADDED\\nIf this remains visible, check for script errors."
