-- New-allergen items said "on its own", but a plan may pair them with foods already had; the day's note explains.
update food_plan_item
set reason = replace(reason, ': on its own, in a small amount, so you can spot any reaction.', ', in a small amount.')
where kind = 'new_allergen' and source = 'app';
